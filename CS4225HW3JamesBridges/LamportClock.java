public class LamportClock {
	private int time;
	private final int nodeId;

	public LamportClock(int nodeId) {
		this.time = 0;
		this.nodeId = nodeId;
	}

	// For local events
	public synchronized void tick() {
		time += nodeId;
	}

	// For received events
	public synchronized void update(int receivedTime) {
		time = Math.max(time, receivedTime) + nodeId;
	}

	public synchronized int getTime() {
		return time;
	}
}
