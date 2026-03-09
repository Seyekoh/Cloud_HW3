// Class to prevent false sharing by padding to cache line size (64 bytes)
public class PaddedCounter {
	// Cache line padding - 56 bytes (7 longs of 8 bytes each)
	private volatile long p1, p2, p3, p4, p5, p6, p7;

	// The actual counter value - 8 bytes
	private volatile long value;

	// Padding on the other side to prevent adjacency with next object
	private volatile long q1, q2, q3, q4, q5, q6, q7;

	public PaddedCounter() {
		this.value = 0;
	}

	public void increment() {
		value++;
	}

	public long getValue() {
		return value;
	}

	// For debugging - shows memory layout approximation
	public void printSize() {
		System.out.println("PaddedCounter size ~64 bytes");
	}
}
