import java.util.Calendar;
import java.util.LinkedList;



public class ConcurrencyController {
	
	private LinkedList<Calendar> queue;
	
	public ConcurrencyController(){
		queue = new LinkedList<Calendar>();
	}
	
	public void add(Calendar c){
		queue.addLast(c);
	}
	
	public boolean isNext(Calendar c){
		if(queue.getFirst().equals(c)){
			return true;
		}else{
			return false;
		}
	}
	
	public void release(){
		queue.poll();
		if(queue.size()>0){
			queue.peek().notifyAll();
		}
	}

}
