package dbn;

import utils.BidirectionalArray;

public class NumericAttribute implements Attribute {

	private String name;

	private BidirectionalArray<Float> values = new BidirectionalArray<Float>();

	@Override
	public boolean isNumeric() {
		return true;
	}

	@Override
	public boolean isNominal() {
		return false;
	}

	@Override
	public int size() {
		return values.size();
	}

	@Override
	public boolean add(String value) {
		return values.add(Float.parseFloat(value));
	}

	@Override
	public String toString() {
		return "" + values;
	}

	@Override
	public int getIndex(String value) {
		return values.getIndex(Float.parseFloat(value));
	}

	@Override
	public String get(int index) {
		return Float.toString(values.get(index));
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof NumericAttribute))
			return false;
		NumericAttribute other = (NumericAttribute) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
	
	

}
