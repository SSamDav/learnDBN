package dbn;

/**
 * 
 * An attribute is a representation of a random variable in a DBN. It can be
 * numeric (discrete valued) or nominal and takes a finite number of different
 * values. Values are indexed by sequential integers, which are used for
 * representing them.
 * 
 * @author josemonteiro
 * 
 */

public interface Attribute {

	/**
	 * Function that says if an attribute is Numeric.
	 * @return boolean Returns true if the attribute is numeric.
	 */
	public boolean isNumeric();
	
	/**
	 * Function that says if an attribute is Nominal.
	 * @return boolean Returns true if the attribute is nominal.
	 */
	public boolean isNominal();

	/**
	 * Function that returns the number of possible values that an attribute can assume.
	 * @return int the number of possible values that an attribute can assume.
	 */
	public int size();
	
	/**
	 * Function that returns the corresponding value of an attribute.
	 * @param index The index of the corresponding value.
	 * @return String Corresponding value that the attribute assume.
	 */
	public String get(int index);

	/**
	 * Function that returns the corresponding index of an value that the attribute assume.
	 * @param value Value that the attribute. 
	 * @return int Corresponding index of the assumed value.
	 */
	public int getIndex(String value);

	/**
	 * Adds a new value that the attribute assumes.
	 * @param value Corresponding value that the attribute assumes.
	 * @return boolean Returns true if the values is added and false if the attribute already has this value.
	 */
	public boolean add(String value);

	public String toString();

	/**
	 * Setter for the name of the attribute.
	 * @param name Name of the attribute.
	 */
	public void setName(String name);

	/**
	 * Getter for the name of an attribute.
	 * @return String The name of the attribute.
	 */
	public String getName();

}
