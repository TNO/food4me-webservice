package eu.qualify.food4me.measurements

import eu.qualify.food4me.ModifiedProperty
import eu.qualify.food4me.Property
import eu.qualify.food4me.interfaces.Measurable
import groovy.transform.Canonical

 @Canonical
 class Measurements {
	List<Measurement> measurements
	
	public Measurements() {
		measurements = []
	}
	
	List<Measurement> getAll() {
		measurements
	}
	
	void add(Measurement measurement) {
		if( measurement )
			measurements << measurement
	}
	
	void addAll(Collection<Measurement> measurements ) {
		this.measurements.addAll(measurements.findAll())
	}
	
	public MeasuredValue getValueFor( Measurable p ) {
		return measurements.find { it.property == p }?.value
	}
	
	/**
	 * Returns a list of values for the given property, including modified properties
	 * @param p
	 * @return
	 */
	public List<Measurement> getValuesFor( Property p ) {
		return measurements.findAll {
			it?.property?.rootProperty == p 
		}
	}

	/**
	 * Returns a list with properties for which we have measurements 
	 * @param propertyGroup
	 * @return
	 */
	public List<Property> getAllPropertiesForPropertyGroup( String propertyGroup ) {
		measurements.collect {
			if( it?.property?.rootProperty?.propertyGroup != propertyGroup )
				return null
			
			it?.property?.rootProperty
		}.findAll().unique()
	}
	
}
