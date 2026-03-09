import java.util.*;
import java.util.concurrent.atomic.*;

public class Metrics {
	private final long startTime;
	private final long endTime;
	private final long totalEvents;
	private final int messagesSent;
	private final int messagesReceived;
	private final int finalLamportTime;
	private final List<Long> counterValues;
	private final String optimizationType;
	private final String nodeId;

	public Metrics(String nodeId, String optimizationType, long startTime, long endTime, long totalEvents, int messagesSent, int messagesReceived, int finalLamportTime, List<Long> counterValues) {
		this.nodeId = nodeId;
		this.optimizationType = optimizationType;
		this.startTime = startTime;
		this.endTime = endTime;
		this.totalEvents = totalEvents;
		this.messagesSent = messagesSent;
		this.messagesReceived = messagesReceived;
		this.finalLamportTime = finalLamportTime;
		this.counterValues = counterValues;
	}

	public long getExecutionTime() {
		return endTime - startTime;
	}

	public double getThroughput() {
		return (double) totalEvents / (getExecutionTime() / 1000.0); // events per second
	}

	@Override
	public String toString() {
		return String.format("%s,%s,%d,%d,%.2f,%d,%d,%d,%s", nodeId, optimizationType, getExecutionTime(), totalEvents, getThroughput(), messagesSent, messagesReceived, finalLamportTime, counterValues);
	}

	public static String getHeader() {
		return "NodeID,Optimization,ExecTime(ms),TotalEvents,Throughput(events/sec),MsgSent,MsgReceived,FinalLamport,CounterValues";
	}
}
