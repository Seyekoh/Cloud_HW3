public class Counter {
	private long value;

	public Counter() {
		this.value = 0;
	}

	public void increment() {
		value++;
	}

	public long getValue() {
		return value;
	}
}
