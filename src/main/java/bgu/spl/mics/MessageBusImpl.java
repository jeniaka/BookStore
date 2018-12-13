package bgu.spl.mics;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The {@link MessageBusImpl class is the implementation of the MessageBus interface.
 * Write your implementation here!
 * Only private fields and methods can be added to this class.
 */
public class MessageBusImpl implements MessageBus {
	private Map<Class, LinkedBlockingQueue<MicroService>> roundRobinMap;
	private Map<MicroService, LinkedBlockingQueue<Message>> microServiceQueueMap;
	private Map<Message, Future> eventFutureMap;
	private Map<MicroService, LinkedBlockingQueue<Future>> microServiceFutureMap;
	private Object obj = new Object();

    private static class MessageBusSingelton{
        private static MessageBusImpl instance = new MessageBusImpl();
    }

	public MessageBusImpl() {
		this.roundRobinMap = new ConcurrentHashMap<>();
		this.microServiceQueueMap = new ConcurrentHashMap<>();
		this.eventFutureMap = new ConcurrentHashMap<>();
		this.microServiceFutureMap = new ConcurrentHashMap<>();
	}

	public static MessageBusImpl getInstance()
    {
        return MessageBusSingelton.instance;
    }
	@Override
	public <T> void subscribeEvent(Class<? extends Event<T>> type, MicroService m) {
    	subscribe(type, m);
     }

	@Override
	public void subscribeBroadcast(Class<? extends Broadcast> type, MicroService m) {
    	subscribe(type, m);
	}

	private void subscribe(Class type, MicroService m) {
		if (type == null || m == null)
			return;
		synchronized (obj) {
			roundRobinMap.putIfAbsent(type, new LinkedBlockingQueue<MicroService>());
            try {
                roundRobinMap.get(type).put(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
	}

	@Override
	public <T> void complete(Event<T> e, T result) {
        synchronized (eventFutureMap.get(e)) {
            eventFutureMap.get(e).resolve(result);//TODO: need Sync??
            eventFutureMap.get(e).notifyAll();
        }
	}

	@Override
	public void sendBroadcast(Broadcast b) {
        if(b == null)
            return;
		Queue<MicroService> tempMicroServiceQueue = roundRobinMap.get(b.getClass());
		if (tempMicroServiceQueue == null)
		    return;
        for (MicroService micro: tempMicroServiceQueue) {
                 addMessageToMicroService(b, micro);
             }

//		if(tempMicroServiceQueue!=null) {
//            synchronized (tempMicroServiceQueue) {
//                for (MicroService tempMicroService : tempMicroServiceQueue) {
//                    Queue<Message> tempMessagesQueue = microServiceQueueMap.get(tempMicroService);
//                    if (tempMessagesQueue == null) {
//                        synchronized (tempMessagesQueue) {
//                            tempMessagesQueue.add(b);
//                        }
//                    }
//                }
//            }
//        }
	}
	private void addMessageToMicroService(Message msg, MicroService m){
            LinkedBlockingQueue<Message> tempMicroQueue = microServiceQueueMap.get(m);
            if (tempMicroQueue == null)
                return;
            synchronized (tempMicroQueue) {
                try {
                    tempMicroQueue.put(msg);
            } catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }




	
	@Override
	public <T> Future<T> sendEvent(Event<T> e) {
        MicroService m = null;
		LinkedBlockingQueue<MicroService> tempMicroServiceQueue = roundRobinMap.get(e.getClass());
		if (tempMicroServiceQueue == null || tempMicroServiceQueue.isEmpty())
		    return null;

        try {
            m = tempMicroServiceQueue.take();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        addMessageToMicroService(e, m);
//		synchronized (tempMicroServiceQueue){
//			try {
//				m = tempMicroServiceQueue.take();
//			} catch (InterruptedException e1) {
//				e1.printStackTrace();
//			}
//			tempMicroServiceQueue.add(m);
//			microServiceQueueMap.get(m).add(e);
//		}
		Future<T> f1 = new Future<>();
		eventFutureMap.putIfAbsent(e, f1);
		LinkedBlockingQueue<Future> tempFutureQueue =  microServiceFutureMap.get(m);
		tempFutureQueue.add(f1);
		return f1;
	}

	@Override
	public void register(MicroService m) {
		if (microServiceQueueMap.get(m) == null) {


			microServiceQueueMap.putIfAbsent(m, new LinkedBlockingQueue<Message>());
			microServiceFutureMap.putIfAbsent(m, new LinkedBlockingQueue<Future>());
		}
	}

	@Override
	public void unregister(MicroService m) {
    	synchronized (m){
			LinkedBlockingQueue<Future> tempFutureQueue = microServiceFutureMap.get(m);
			for (Future f1 : tempFutureQueue){
			    if(!f1.isDone())
				    f1.resolve(null);
			    tempFutureQueue.remove(f1);
			}

            for (Class cls:roundRobinMap.keySet()) {
                LinkedBlockingQueue<MicroService> tempMicroServiceQueue = roundRobinMap.get(cls);
                tempMicroServiceQueue.remove(cls);
            }

		}
	}

	@Override
	public Message awaitMessage(MicroService m) throws InterruptedException {
        Message msgToSend = null;
        try{
            LinkedBlockingQueue<Message> tempMessageQueue = microServiceQueueMap.get(m);
            if (tempMessageQueue == null)
                return null;
            msgToSend = tempMessageQueue.take();
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            return null;
        }
		return msgToSend;
	}

	

}
