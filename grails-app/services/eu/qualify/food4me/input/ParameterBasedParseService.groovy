package eu.qualify.food4me.input

import java.awt.event.ItemEvent;
import java.util.List;

import eu.qualify.food4me.ModifiedProperty
import eu.qualify.food4me.Property
import eu.qualify.food4me.Unit
import eu.qualify.food4me.interfaces.Advisable
import eu.qualify.food4me.interfaces.Measurable
import eu.qualify.food4me.interfaces.Parser
import eu.qualify.food4me.measurements.MeasuredValue
import eu.qualify.food4me.measurements.Measurement
import eu.qualify.food4me.measurements.MeasurementStatus
import eu.qualify.food4me.measurements.Measurements
import grails.converters.JSON
import grails.transaction.Transactional

class ParameterBasedParseService implements Parser {
	protected final String MODIFIER_TOTAL = "total"
	
	protected conversionMap = [
		generic: Property.PROPERTY_GROUP_GENERIC,
		nutrient: Property.PROPERTY_GROUP_NUTRIENT,
		biomarker: Property.PROPERTY_GROUP_BIOMARKER,
		snp: Property.PROPERTY_GROUP_SNP,
		physical: Property.PROPERTY_GROUP_PHYSICAL,
		foodgroup: Property.PROPERTY_GROUP_FOODGROUP
	]

	/**
	 * Parse the measurements as given in the parameters
	 * 
	 * Each parameter should be given in the format
	 * 		[group].[property] = value
	 *   or
	 *      [group].[nutrient].[origin] = value
	 */
	@Override
	public Measurements parseMeasurements(def params) {
		def output = new Measurements()

		conversionMap.each { groupName, group ->
			if( params[groupName] ) {
				log.trace "Parsing the measurements for group " + groupName

				def data = params[groupName]
				if( !data || !(data instanceof Map) ) {
					log.warn "Provided parameters for " + groupName + " are invalid"
					return
				} 
				
				data.each { propertyName, valueData ->
					// grails also adds the combination of parameters to the map
					// Those values include a dot, and should be skipped
					if( propertyName.contains( "." ) )
						return
						
					log.trace "  Property " + propertyName + " in group " + groupName
					
					// Find the given property
					def property = Property.findByEntityIlikeAndPropertyGroup( propertyName, conversionMap[ groupName ] )
					
					if( !property ) {
						log.warn "Cannot find property ${propertyName} in group ${groupName}. Skipping this measurement"
						return
					}
	
					// Nutrients are treated differently, as the intake is split into groups
					if( groupName.toLowerCase() == "nutrient" ) {
						if( valueData instanceof Map ) {
							output.addAll parseNutrient( valueData, property )
						}
					} else {
						output.add parseMeasurement( property, property.unit, valueData)
					}
				}
	
			}
		}

		output
	}
	
	/**
	 * Parse a single measurement
	 * @param measurable	Property to measure
	 * @param unit			Unit that is expected 
	 * @param data			Map with keys value and unit
	 * @return
	 */
	protected Measurement parseMeasurement( Measurable measurable, Unit unit, String data ) {
		// Check if the value is the correct format { value: ..., unit: "" }
		if( !data ) {
			log.warn "The value specified for ${measurable} is invalid: " + data
			return
		}
			
		// Create a value and measurement object
		log.trace "  Parsing measurement for ${measurable}: ${data}"
		
		// Parameters are always specified as string. If the value is numeric, we should treat is as such
		def measuredValue
		if( data.isNumber() ) {
			measuredValue = MeasuredValue.fromValue( data.toBigDecimal() )
		} else {
			measuredValue = MeasuredValue.fromValue( data )
		}
		
		measuredValue.unit = unit
		
		new Measurement(property: measurable, value: measuredValue )
	}
		
	protected List<Measurement> parseNutrient( def valueData, Measurable measurable ) {
		List<Measurement> measurements = []
		
		// The data is split up into foodgroups. The allowed foodgroups are specified as 
		// a value in the ModifiedProperty.Modifier enum
		def property
		valueData.each { modifier, groupData ->
			// Handle the special modifier 'total', as it should be stored without modifier
			if( modifier.toLowerCase() == MODIFIER_TOTAL ) {
				property = measurable
			} else {
				// Check if this modifier is supported. If not, skip this measurement
				def modifierObject = ModifiedProperty.Modifier.values().find { it.id.toLowerCase() == modifier.toLowerCase() }
				if( !modifierObject ) {
					log.warn "The group " + modifier + " to be imported for " + measurable + " is not supported. " + 
						"Please note that the totals for this nutrient may not be accurate as this measurement is not used." 
					return
				}
				
				property = new ModifiedProperty( property: measurable, modifier: modifierObject.id )
			}
			
			// Parse the measurement itself
			measurements << parseMeasurement( property, measurable.unit, groupData)
		}

		measurements
	}
	
	@Override
	public MeasurementStatus parseStatus(def input) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Advisable> parseEntityList(def input) {
		// TODO Auto-generated method stub
		return null;
	}

}
