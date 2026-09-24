package dev.owczon.vectraclient.module;

/**
 * A single tunable value owned by a {@link Module}.
 *
 * Only two kinds exist for now - a clamped/stepped number and a boolean - which is enough for the
 * current modules and keeps the ClickGUI (one slider or one button per setting) simple.
 */
public abstract class Setting<T> {

	private final String name;

	private Setting(String name) {
		this.name = name;
	}

	public final String getName() {
		return name;
	}

	public abstract T get();

	public abstract void set(T value);

	/** A number bounded by [min, max] and snapped to {@code step}, for use with a GUI slider. */
	public static final class DoubleSetting extends Setting<Double> {

		private final double min;
		private final double max;
		private final double step;
		private double value;

		DoubleSetting(String name, double value, double min, double max, double step) {
			super(name);
			this.min = min;
			this.max = max;
			this.step = step;
			this.set(value);
		}

		@Override
		public Double get() {
			return value;
		}

		@Override
		public void set(Double value) {
			double clamped = Math.max(min, Math.min(max, value));
			if (step > 0.0) {
				// Snap onto the nearest step so sliders land on readable values (3.0, not 2.978).
				clamped = min + Math.round((clamped - min) / step) * step;
				clamped = Math.max(min, Math.min(max, clamped));
			}
			this.value = clamped;
		}

		public double getMin() {
			return min;
		}

		public double getMax() {
			return max;
		}
	}

	/** A simple on/off value, rendered as a toggle button. */
	public static final class BooleanSetting extends Setting<Boolean> {

		private boolean value;

		BooleanSetting(String name, boolean value) {
			super(name);
			this.value = value;
		}

		@Override
		public Boolean get() {
			return value;
		}

		@Override
		public void set(Boolean value) {
			this.value = value;
		}
	}
}
