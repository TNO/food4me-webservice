/*
 *  Copyright (C) 2015 The Hyve
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.qualify.food4me.importer

import eu.qualify.food4me.StoredLog
import eu.qualify.food4me.Property
import eu.qualify.food4me.Unit
import eu.qualify.food4me.decisiontree.Advice
import eu.qualify.food4me.decisiontree.AdviceCondition
import eu.qualify.food4me.decisiontree.AdviceText
import eu.qualify.food4me.measurements.Status
import eu.qualify.food4me.reference.ReferenceCondition
import eu.qualify.food4me.reference.ReferenceValue
import groovy.sql.Sql


class ImportService {
	static transactional = false
	
	def sessionFactory
	def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP
	def grailsApplication
	def dataSource
	
	int batchSize = 50
	String separatorChar = "\t"

    // Logger object that stored the messages in addition to logging them to log4j
    StoredLog storedLog = new StoredLog(log)

	def loadAll( String directory = null ) {
		loadUnitsFromDirectory( directory )
		loadPropertiesFromDirectory( directory )
		loadReferencesFromDirectory( directory )
		loadDecisionTreesFromDirectory( directory )
		loadAdviceTextsFromDirectory( directory )
	}
	
	/**
	 * Loads units from a CSV inputstream
	 * @param inputStream
	 * @return
	 */
	def loadUnits( InputStream inputStream ) {
		def units = []
		def alreadyImportedIds = []
		
		inputStream.toCsvReader([skipLines: 1, separatorChar: separatorChar]).eachLine { line ->
			if( !line || line.size() < 3 ) {
                storedLog.warn "Skipping line as it has not enough columns: " + line?.size()
				return
			}
			
			if( !line[0] ) {
                storedLog.trace "Skipping empty line"
				return
			}
			
			// Check if a unit with this externalId already exists
			def externalId = line[2] ?: line[3]
			if( Unit.countByExternalId( externalId ) > 0 || externalId in alreadyImportedIds) {
                storedLog.info( "Skip importing unit " + line[0] + " / " + externalId + " because it already exists" )
			} else {
				units << new Unit( name: line[0], externalId: externalId, code: line[1] )
                storedLog.trace( "Importing unit " + line[0] + " / " + externalId )
				alreadyImportedIds << externalId
			}
				 
			if( units.size() >= batchSize ) {
				saveBatch( Unit, units )
				units.clear()
			}
		}
		
		// Save all remaining units
		saveBatch( Unit, units )

	}
	
	/**
	 * Loads properties into the database from a CSV inputstream
	 * @return
	 */
	def loadProperties( InputStream inputStream ) {
		def properties = []
		def alreadyImportedIds = []
		
		inputStream.toCsvReader([skipLines: 1, separatorChar: separatorChar]).eachLine { line ->
			if( !line || line.size() < 3 ) {
                storedLog.warn "Skipping line as it has not enough columns: " + line?.size()
				return
			}
			
			if( !line[0] ) {
                storedLog.trace "Skipping empty line"
				return
			}
			
			// If no external ID is given, the property cannot be added
			// We use the snomedct id initially, but if it is not given, we use the EuroFIR code
			def externalId = line[2] ?: line[3]
			if( !externalId ) {
                storedLog.warn "Skipping line with property " + line[0] + " as it has no external identifier"
				return
			}
			
			// Check if a property with this externalId already exists
			if( Property.countByExternalId( externalId ) > 0 || externalId in alreadyImportedIds ) {
                storedLog.info( "Skip importing property " + line[0] + " (" + externalId + ") because it already exists" )
			} else {
				// Find the unit to use for this property
				def unitCode = line.size() >= 5 ? line[4] : null
				def unit
				
				if( unitCode ) {
					unit = Unit.findByCode( unitCode )
					if( !unit ) {
                        storedLog.warn "Unit " + unitCode + " for property " + line[0] + " can not be found. Consider importing units first."
						return;
					}
				}
				
				properties << new Property( entity: line[0], propertyGroup: line[1], externalId: externalId, unit: unit )
				alreadyImportedIds << externalId
                storedLog.trace( "Importing property " + line[0] + " / " + line[1] + " with external ID " + externalId )
			}
				 
			if( properties.size() >= batchSize ) {
				saveBatch( Property, properties )
				properties.clear()
			}
		}
		
		// Save all remaining units
		saveBatch( Property, properties )
	}
	
	/**
	 * Loads generic references into the database from a CSV inputstream
     *
     * Format:  2 header lines and all other lines contain the actual references
     *          Column 1:  Property name (e.g. BMI)
     *          Column 2:  Property group (e.g. Physical)
     *          Column 3:  Unit (e.g. kg/m2)
     *
     *          After those columns, some columns with extra parameters for the references occur.
     *          Common examples are age and gender. This is specified in the header as follows
     *            Column x, row 1:  Property name to use as parameter
     *            Column x, row 2:  Either 'Lower boundary', 'Upper boundary' or 'Exact match' (last one is default)
     *
     *          After the extra parameters, an empty column is required to denote the start of the
     *          actual property values. There are 2 columns for each status (except for the last one).
     *            Column x, row 1:  Name of the status ('Very low', 'Low', 'OK', 'High', 'Very high')
     *            Column x, row 2:  'Color'
     *            Column x + 1, row 2:  'Upper boundary'
     *
     *          In each data row, a color and upper boundary for that status can be given. The uppser boundary for
     *          a status is used as lower boundary for the next status. A reference value will be stored for
     *          each status having a color. If you don't specify boundary values for those statusses, the system
     *          may produce unexpected outcome.
     * @return
	 */
	def loadGenericReferences( InputStream inputStream ) {
		def references = []
        def headerLines = []
        def structure
        def lineNo = 0

		// The first 2 lines contain the headers
		inputStream.toCsvReader([skipLines: 0, separatorChar: separatorChar]).eachLine { line ->
			if( !line || line.size() < 3 ) {
                storedLog.warn "Skipping line as it has not enough columns: " + line?.size()
				return
			}

            // Combine the first three header lines to be parsed separately
            if( lineNo++ < 2 ) {
                headerLines << line
                return;
            }

            // If we reach this point, we should first parse the header lines
            if( headerLines ) {
                structure = parseReferencesHeaderLines( headerLines )
                headerLines = null
            }

            if( !line[0] ) {
                storedLog.trace "Skipping empty line"
				return
			}

			// Find the property that this reference refers to
			def property = Property.findByEntityAndPropertyGroup( line[0], line[1] )
			
			if( !property ) {
                storedLog.warn "Cannot find entity " + line[0] + " / " + line[1] + " when importing reference. Skipping this line"
				return
			}

            //

			// Loop through the possible references. Each reference has 2 columns:
			//		color and upper boundary. The upper boundary is also used as
			//		the lower boundary of the next
			def currentLowerBoundary = null
			def color
			
			structure.statuses.each { columnNo, status ->
				// If no status color is given for this status, we skip this status
				if( line.size() <= columnNo || !line[ columnNo ] ) {
                    storedLog.trace "Status " + status + " not found for property " + property
					return
				}
				
				// TODO: check for duplicates
				color = line[ columnNo ]
				def upperBoundary = ( line.size() > columnNo + 1 && line[ columnNo + 1 ].isBigDecimal() ) ? line[ columnNo + 1 ].toBigDecimal() : null
				
				def reference = new ReferenceValue(subject: property, status: status, color: Status.Color.fromString( color ) )

				// Add the condition on the property itself
				reference.addToConditions( new ReferenceCondition( subject: property, low: currentLowerBoundary, high: upperBoundary, conditionType: ReferenceCondition.TYPE_NUMERIC ) )

                // Add extra parameters specified in the file
                structure.parameterColumns.each { propertyId, parameterInfo ->
                    def parameterProperty = parameterInfo.property
                    def condition = new ReferenceCondition( subject: parameterProperty )

                    parameterInfo.columns.each { columnInfo ->
                        def parameterValue = line[columnInfo.column]

                        if( !parameterValue )
                            return

                        storedLog.trace( "Adding reference condition on " + parameterProperty + ": " + columnInfo.type + " - " + parameterValue  )
                        switch( columnInfo.type.toLowerCase() ) {
                            case "lower boundary":
                                if( parameterValue && parameterValue.isBigDecimal() ) {
                                    condition.low = parameterValue.toBigDecimal()
                                    condition.conditionType = ReferenceCondition.TYPE_NUMERIC
                                }
                                break;
                            case "upper boundary":
                                if( parameterValue && parameterValue.isBigDecimal() ) {
                                    condition.high = parameterValue.toBigDecimal()
                                    condition.conditionType = ReferenceCondition.TYPE_NUMERIC
                                }
                                break;
                            case "exact match":
                            default:
                                if( parameterValue ) {
                                    condition.value = parameterValue
                                    condition.conditionType = ReferenceCondition.TYPE_TEXT
                                }
                                break;
                        }

                    }

                    // Store the condition with the reference, if the conditionType has been set
                    // This check prevents 'empty' conditions to be added
                    if( condition.conditionType )
                        reference.addToConditions(condition)
                }

                storedLog.trace( "Importing reference for " + property + " / " + status + " with " + reference.conditions?.size() + " conditions " + currentLowerBoundary + " / " + upperBoundary  )
				references << reference
				
				// Prepare for next iteration
				currentLowerBoundary = upperBoundary
			}
			
			if( references.size() >= batchSize ) {
				saveBatch( ReferenceValue, references )
				references.clear()
			}
		}
		
		// Save all remaining references
		saveBatch( ReferenceValue, references )
	}
	
	/**
	 * Loads SNP references into the database from a CSV inputstream
	 * @return
	 */
	def loadSNPReferences( InputStream inputStream ) {
		def references = []
		
		// The first 2 lines
		def lineNo = 1
		def columnStatus = [:]
		inputStream.toCsvReader([separatorChar: separatorChar]).eachLine { line ->
			if( !line || line.size() < 2 ) {
                storedLog.warn "Skipping line as it has not enough columns: " + line?.size()
				return
			}
			
			// Parse the header line, to see where the risk-alleles and non-risk alleles are
			if( lineNo++ == 1 ) {
				def columnNo = 1
				def currentStatus = ""
				while( columnNo < line.size() ) {
					if( line[ columnNo ] )
						currentStatus = line[ columnNo ]
					
					columnStatus[columnNo] = currentStatus
					columnNo++
				}
				
				lineNo++
				return
			}
			
			if( !line[0] ) {
                storedLog.trace "Skipping empty line"
				return
			}
			
			// Find the SNP that this reference refers to
			def snp = Property.findByEntityAndPropertyGroup( line[0], Property.PROPERTY_GROUP_SNP )
			
			if( !snp ) {
                storedLog.warn "Cannot find SNP " + line[0] + " when importing reference. Skipping this line"
				return
			}
			
			// Loop through all columns, and store a reference for the allele
			def columnNo = 1
			while( columnNo < line.size() ) {
				if( line[ columnNo ] ) {
					def status = columnStatus[columnNo]
					
					// Color is not relevant for SNPs, but we store a color anyhow
					def color = ( status == "Risk allele" ) ? Status.Color.RED : Status.Color.GREEN

                    storedLog.trace "Storing SNP " + line[0] + " / " + line[ columnNo ] + " as " + status
					
					def reference = new ReferenceValue(subject: snp, status: status, color: color )
					reference.addToConditions( new ReferenceCondition( subject: snp, value: line[ columnNo ], conditionType: ReferenceCondition.TYPE_TEXT ) )
					references << reference
				}
				
				columnNo++
			}
			
			if( references.size() >= batchSize ) {
				saveBatch( ReferenceValue, references )
				references.clear()
			}
		}
		
		// Save all remaining references
		saveBatch( ReferenceValue, references )
	}

	/**
	 * Loads decision trees from a CSV inputstream
	 * 
	 * Format: 	Cel A1 contains the property that this decision tree is about
	 * 			Row 1 (from column B) contains the properties to decide on
	 *			Row 2 (from column B) contains optional modifiers on the properties
	 *			Row 3 (from column B) contains either Status or Value
	 * The lines below that contain the advices, the first column contains the advice code, 
	 * the other columns contain the value or status of the given variable
	 *
	 * @param inputStream
	 * @return
	 */
	def loadDecisionTrees( InputStream inputStream ) {
		def adviceObjects = []
		
		def adviceSubject
		def conditionSubjects = []
		
		def lineNo = 1
		def headerLines = []
		def structure
		
		inputStream?.toCsvReader([skipLines: 0, separatorChar: separatorChar]).eachLine { line ->
			if( !line || line.size() < 2 ) {
                storedLog.warn "Skipping line as it has not enough columns: " + line?.size()
				return
			}
			
			// Combine the first three header lines to be parsed separately
			if( lineNo++ < 4 ) {
				headerLines << line
				return;
			}
			
			// If we reach this point, we should first parse the header lines
			if( headerLines ) {
				structure = parseDecisionTreeHeaderLines( headerLines )
				headerLines = null
			}
			
			// If the header lines could not be properly parsed, there is no need
			// to continue, as we can't store anything
			if( !structure ) {
				return
			}
			
			if( !line[0] ) {
                storedLog.trace "Skipping empty line"
				return
			}
			
			// As the status in the file could be 'below OK' or 'above OK',
			// it should be translated into Low and Very Low and the same for above OK.
			// That means, multiple advices could be generated for each line.
			//
			// To handle that, we create a list with a list of options for each column. That means
			// if we start with a line like [advice1, Below OK, High, Above OK], it would result in
			// a list as follows: [ [Very Low, Low], [High], [Above OK] ]. We will later on create
			// combinations of these conditions to end up with all required records in the database.
			//
			// Please note, in order to properly generate the combinations, we will insert a list with 
			// NULL value for each empty cell. These will be discarded when generating the domain objects
			// itself.
			log.trace "Generate advice combinations for advice " + structure.adviceSubject + " / " + line[0]
			
			def conditions = []
			
			def columnNo = 1
			def translationMap = [
				"below ok": [ Status.STATUS_VERY_LOW, Status.STATUS_LOW ],
				"above ok": [ Status.STATUS_VERY_HIGH, Status.STATUS_HIGH ],
				"ok and lower": [ Status.STATUS_OK, Status.STATUS_LOW, Status.STATUS_VERY_LOW ],
				"ok and higher": [ Status.STATUS_OK, Status.STATUS_HIGH, Status.STATUS_VERY_HIGH ],
			]
			
			while( columnNo < line.size() ) {
				if( line[columnNo] ) {
					def currentValue = line[columnNo]?.trim()
					
					// Check if we need to translate this property into multiple statusses
					// That is, if the column is set to filter on status and we have a translation for this status
					if( structure.conditionSubjects[columnNo].filterOnStatus && translationMap.containsKey( currentValue.toLowerCase() ) ) {
						conditions << translationMap[currentValue.toLowerCase()]
					} else {
						conditions << [currentValue]
					}
				} else {
					// Add a dummy value for this condition, in order to make the combinations
					// method work properly later on 
					conditions << [null]
				}
				
				columnNo++
			}
						
			if( !conditions || !conditions.findAll() ) {
                storedLog.warn "No conditions are found for advice with code " + line[0]
			}
			// Generate combinations of all conditions
			def adviceConditions = conditions.combinations()
			
			// Generate objects for all advices
			adviceConditions.each { conditionSet ->
				def advice = new Advice( code: toAdviceCode(line[0]), subject: structure.adviceSubject ) 

                storedLog.trace "  Generating advice with conditions + " + conditionSet
				
				conditionSet.eachWithIndex { conditionValue, index ->
					if( conditionValue ) {
						// Retrieve the parameters for this column
						def conditionParams = structure.conditionSubjects[index+1]
						
						def condition = new AdviceCondition( subject: conditionParams.property, modifier: conditionParams.modifier )
						if( conditionParams.filterOnStatus ) {
							condition.status = conditionValue
						} else {
							condition.value = conditionValue
						}
						
						advice.addToConditions condition
					}
				}
				
				adviceObjects << advice
			}
			
			if( adviceObjects.size() >= batchSize ) {
				saveBatch( Advice, adviceObjects )
				adviceObjects.clear()
			}

		}
		
		// Save all remaining objects
		saveBatch( Advice, adviceObjects )
	}
	
	protected def parseDecisionTreeHeaderLines( def headerLines ) {
		def decisionTreeStructure = [
			adviceSubject: null,
			conditionSubjects: [:]
		]
		
		if( headerLines.size() != 3 ) {
            storedLog.error "Invalid number of header lines for decision tree: " + headerLines.size() + " lines. Skipping import of this file"
			return null
		}
		
		if( headerLines[0].size() != headerLines[1].size() || headerLines[0].size() != headerLines[2].size() ) {
            storedLog.error "Invalid format of header lines for decision tree: all header lines should be equal length. Sizes are: " + headerLines.collect { it.size() } + ". Skipping import of this file."
			return null
		}
		
		// The first cell contains the subject of the advice
		decisionTreeStructure.adviceSubject = Property.findByEntity( headerLines[0][0].trim() )
		
		if( !decisionTreeStructure.adviceSubject ) {
            storedLog.warn "No property could be found for advice subject " + headerLines[0][0] + ". Skipping import of this file."
			return
		}

		// The rest of the columns contain the properties
		def columnNo = 1
		def conditionProperty
		while( columnNo < headerLines[0].size() ) {
			if( headerLines[0][columnNo] ) { 
				conditionProperty = Property.findByEntity( headerLines[0][columnNo].trim() )
				
				if( !conditionProperty ) {
                    storedLog.warn "Cannot find property for column ${columnNo}: " + headerLines[0][columnNo] + ". Skipping import of this file."
					return null
				}
				
				def filterOnValue = headerLines[2][columnNo]?.trim()?.toLowerCase() == "value"
				
				// Check whether we know any references for the given property. If not, the status should be given by the user
				if( !filterOnValue && ReferenceValue.countBySubject( conditionProperty ) == 0 ) {
                    storedLog.warn "Cannot find any references for " + headerLines[0][columnNo] + ". This means that we can't determine a status for this variable automatically."
				}
				
				decisionTreeStructure.conditionSubjects[ columnNo ] = [
					property: conditionProperty,
					modifier: headerLines[1][columnNo],
					filterOnValue: filterOnValue,
					filterOnStatus: !filterOnValue
				] 
			}
			
			columnNo++
		}
		
		decisionTreeStructure
	}

    protected def parseReferencesHeaderLines( def headerLines ) {
        def referenceStructure = [
            parameterColumns: [:],
            statuses: [:]
        ]

        if( headerLines.size() != 2 ) {
            storedLog.error "Invalid number of header lines for references: " + headerLines.size() + " lines. Skipping import of this file"
            return null
        }

        if( headerLines[0].size() != headerLines[1].size() ) {
            storedLog.error "Invalid format of header lines for decision tree: all header lines should be equal length. Sizes are: " + headerLines.collect { it.size() } + ". Skipping import of this file."
            return null
        }

        // Skip 3 columns. After that, we expect some parameters to filter on. After that, we expect the statuses to be mentioned
        def columnNo = 3

        // Denotes the part we are working on: either the parameters or the statuses
        def part = 0;
        def property

        while( columnNo < headerLines[0].size() ) {
            // An empty column denotes the boundary between parameters and statuses
            if( !headerLines[0][columnNo] && !headerLines[1][columnNo] ) {
                part = 1;
                columnNo++;
                continue;
            }

            if(part == 0) {
                // Parse header for parameter
                def propertyName = headerLines[0][columnNo]
                def type = headerLines[1][columnNo]

                property = Property.findByEntityIlike( propertyName.trim() )

                if( !property ) {
                    storedLog.warn "Cannot find property for column ${columnNo}: " + headerLines[0][columnNo] + ". This parameter will not be used in determining references!"
                    columnNo++
                    continue
                }

                if( !referenceStructure.parameterColumns.containsKey(property.id) ) {
                    referenceStructure.parameterColumns[property.id] = [property: property, columns: []]
                }

                referenceStructure.parameterColumns[property.id].columns <<
                [
                    column: columnNo,
                    type: type
                ]
            } else {
                // Parse header for status

                // Empty first row is actually not a problem here
                // However, that is not a status
                if( headerLines[0][columnNo] ) {
                    referenceStructure.statuses[columnNo] = headerLines[0][columnNo].trim()
                }
            }

            columnNo++
        }

        referenceStructure
    }
	
	/**
	 * Loads text for advices from a CSV inputstream. 
	 * 
	 * The file should NOT have a header line. The first column contains the code, 
	 * the second column contains the text to be imported
	 * @param inputStream
	 * @return
	 */
	def loadAdviceTexts( InputStream inputStream, String language = "en" ) {
		def objects = []
		
		inputStream.toCsvReader([skipLines: 0, separatorChar: separatorChar]).eachLine { line ->
			if( !line || line.size() < 2 ) {
                storedLog.warn "Skipping line as it has not enough columns: " + line?.size()
				return
			}
			
			if( !line[0] ) {
                storedLog.trace "Skipping empty line"
				return
			}
			
			// Check if a unit with this externalId already exists
			def adviceCode = line[0].trim()
			def translation = line[1]?.trim()
			
			if( !translation ) {
                storedLog.warn "Skipping translation for code " + adviceCode + " as it is empty"
				return
			}
			
			// Check if the translation already exists. If so, overwrite
            def adviceCodeToImport = toAdviceCode(adviceCode)
			def adviceText = AdviceText.findByCodeAndLanguage( adviceCodeToImport, language )
			if( adviceText ) {
                storedLog.trace "Overwriting translation for " + adviceCodeToImport + " in " + language
				adviceText.text = line[1]
			} else {
                storedLog.trace "Importing new for " + adviceCodeToImport + " in " + language
				adviceText = new AdviceText( code: adviceCodeToImport, language: language, text: line[1] )
			}
			
			objects << adviceText
			
			if( objects.size() >= batchSize ) {
				saveBatch( AdviceText, objects )
				objects.clear()
			}

		}
		
		// Save all remaining units
		saveBatch( AdviceText, objects )
	}
	
	/**
	 * Loads units into the database from the file units*.txt in the given directory
	 * @return
	 */
	def loadUnitsFromDirectory( String directory = null ) {
        storedLog.info "Start loading units " + ( directory ? " from " + directory : "" )
		
		importData( directory, ~/units.*\.txt/, { file ->
            storedLog.info( "Loading units from " + file )
			file.withInputStream { is -> loadUnits( is ) }
		})
	}
	
	/**
	 * Loads properties into the database from the file properties*.txt in the given directory
	 * @return
	 */
	def loadPropertiesFromDirectory( String directory = null ) {
        storedLog.info "Start loading properties " + ( directory ? " from " + directory : "" )
		
		importData( directory, ~/properties.*\.txt/, { file ->
            storedLog.info( "Loading properties from " + file )
			file.withInputStream { is -> loadProperties( is ) }
		})
	}
	
	/**
	 * Loads the references into the database from the following files:
	 * 		references_generic*.txt
	 * 		references_snps*.txt
	 * @return
	 */
	def loadReferencesFromDirectory( String directory = null ) {
		loadGenericReferencesFromDirectory( directory )
		loadSNPReferencesFromDirectory( directory )
	}
	
	/**
	 * Loads generic references into the database from the file references_generic*.txt in the given directory
	 * @return
	 */
	def loadGenericReferencesFromDirectory( String directory = null ) {
        storedLog.info "Start loading generic references " + ( directory ? " from " + directory : "" )
		
		// First disable the trigger for advice conditions, as that slows down the import heavily
        storedLog.debug "Disabling trigger on reference_condition"
		disableTriggers "reference_condition"
		
		try {
			importData( directory, ~/references-generic.*\.txt/, { file ->
                storedLog.info( "Loading generic references from " + file )
				file.withInputStream { is -> loadGenericReferences( is ) }
			})
		} finally {
			// Re enable the trigger for advice conditions
            storedLog.info "Re enabling trigger on reference_condition"
			enableTriggers "reference_condition"
		}
		
	}
	
	/**
	 * Loads SNP references into the database from the file references_snps*.txt in the given directory
	 * @return
	 */
	def loadSNPReferencesFromDirectory( String directory = null ) {
        storedLog.info "Start loading SNP references " + ( directory ? " from " + directory : "" )
		
		// First disable the trigger for advice conditions, as that slows down the import heavily
        storedLog.debug "Disabling trigger on reference_conditoin"
		disableTriggers "reference_condition"
		
		try {
			importData( directory, ~/references-snps.*\.txt/, { file ->
                storedLog.info( "Loading SNP references from " + file )
				file.withInputStream { is -> loadSNPReferences(is) }
			})
		} finally {
			// Re enable the trigger for advice conditions
            storedLog.info "Re enabling trigger on reference_condition"
			enableTriggers "reference_condition"
		}
	}

	/**
	 * Loads advice texts into the database from the files advice_texts*.[language].txt in the given directory
	 * @return
	 */
	def loadAdviceTextsFromDirectory( String directory = null ) {
        storedLog.info "Start loading advice texts " + ( directory ? " from " + directory : "" )
		
		importData( directory, ~/advice-texts.*\.[a-zA-Z]+\.txt$/, { file ->
			def match = file.name =~ /([a-zA-Z]+)\.txt$/
			if( !match ) {
                storedLog.warn( "Trying to load advice texts from " + file + " but no proper language was specified" )
				return
			}
			
			def language = match[0][1]

            storedLog.info( "Loading advice texts from " + file + " in language " + language )
			file.withInputStream { is -> loadAdviceTexts(is, language) }
		})
	}

	/**
	 * Loads decision trees into the database from the files decision_trees*.txt in the given directory
	 * @return
	 */
	def loadDecisionTreesFromDirectory( String directory = null ) {
        storedLog.info "Start loading decision trees " + ( directory ? " from " + directory : "" )
		
		// First disable the trigger for advice conditions, as that slows down the import heavily
        storedLog.info "Disabling trigger on advice_condition"
		disableTriggers "advice_condition"
		
		try {
			importData( directory, ~/decision-trees.*\.txt/, { file ->
                storedLog.info( "Loading decision trees from " + file )
				file.withInputStream { is -> loadDecisionTrees(is) }
			})
		} finally {
			// Re enable the trigger for advice conditions
            storedLog.info "Re enabling trigger on advice_condition"
			enableTriggers "advice_condition"
		}
	

	}

	/**
	 * Returns the default import directory to import from
	 * @return
	 */
	public String getDefaultImportDirectory() {
		grailsApplication.config.food4me.importDirectory
	}
			
	/**
	 * Import data from files in the given directory
	 * @param matcher
	 * @param fileHandler
	 * @return
	 */
	protected def importData( String directory = null, def matcher, Closure fileHandler ) {
		if( !directory ) { 
			directory = getDefaultImportDirectory()

			if( !directory ) {
                storedLog.error "No default directory given to import data from. Please specify the configuration value food4me.importDirectory to a readable directory."
				return
			} else {
                storedLog.info "Importing data from default directory in configuration: " + directory
			}
		}
			
		def baseDir = new File( directory )
		
		if( !baseDir.exists() || !baseDir.isDirectory() || !baseDir.canRead() ) {
            storedLog.error "Provided directory " + directory + " is not an existing readable directory. Please check your configuration."
			return
		}
		
		baseDir.eachFileMatch matcher, fileHandler
	}

    /**
     * Unzips the uploaded zip file into a temporary directory
     * @param uploadedFile
     * @return The directory where the file is unzipped
     */
    public File unzipUploadedFile(def uploadedFile) {
        def directory = getNewTemporaryFile()

        // Create the temporary directory
        directory.mkdirs()

        // Move the file to the temporary directory, as we need a File to unzip it
        def file = getNewTemporaryFile("zip")
        uploadedFile.transferTo(file)

        // Unzip the file to that directory
        def u = new org.apache.ant.compress.taskdefs.Unzip()
        u.setSrc(file)
        u.setDest(directory)
        u.execute()

        directory
    }

    protected File getNewTemporaryFile(String extension = null) {
        // Start by defining a new temporary directory
        def rootDirectory = grailsApplication.config.food4me.tmpDirectory
        def subFilename
        def file

        while(true) {
            subFilename = java.util.UUID.randomUUID() as String

            if( extension )
                subFilename += "." + extension

            file = new File( rootDirectory + "/" + subFilename )

            if( !file.exists() )
                break
        }

        file
    }
	
	/**
	 * Store a set of objects and cleanup GORM afterwards
	 * @param domainClass
	 * @param objects
	 * @return
	 */
	protected def saveBatch( def domainClass, def objects ) {
		def numSaves = 0;
		
		if( !objects ) {
            storedLog.warn "No objects of type " + domainClass?.simpleName + " to store"
			return
		}

        storedLog.info "Batch saving " + objects.size() + " objects of type " + domainClass?.simpleName
		
		domainClass.withTransaction {
			objects.each { object ->
				if( !object.save() ) {
                    storedLog.error "Unable to save ${domainClass} object in batch: " + object
					object?.errors?.allErrors?.each { currentError ->
                        storedLog.error "Error occured on field [${currentError?.field}] - [${currentError?.defaultMessage}] for value [${currentError?.rejectedValue}]"
					}
				} else {
					numSaves++
				}
			}
		}
		
		cleanUpGORM()
		return numSaves
	}
	
	/**
	 * Prepares an advice code for storage in the database
	 * @param code
	 * @return
	 */
	protected String toAdviceCode(code) {
		code.replaceAll( /\./, "_" )
	}
	
	protected def disableTriggers( String table ) {
		final Sql sql = new Sql(dataSource)
		sql.execute "ALTER TABLE " + table + " DISABLE TRIGGER USER"
	}
	
	protected def enableTriggers( String table ) {
		final Sql sql = new Sql(dataSource)
		sql.execute "ALTER TABLE " + table + " ENABLE TRIGGER USER"
		
		// Update rows in the table without changing the data itself
		// This will execute the trigger
		sql.execute "UPDATE " + table + " set id = id"
	}
	
	/**
	 * Cleaning up of GORM session caches, which are a performance killer if not flushed regularly
	 * @return
	 */
	protected def cleanUpGORM() {
		def session = sessionFactory.currentSession
		
		if( !session )
			log.warn "No hibernate session could be retrieved"
			
		session?.flush()
		session?.clear()
		propertyInstanceMap.get().clear()
	}
}
