<html>
	<head>
		<meta name="layout" content="main" />
		<title>Food4me advices</title>
	</head>
	<body>
		<h1>Food4me status</h1>
		
		<table id="status">
			<thead>
				<tr>
					<th>Property</th>
					<th>Measured value</th>
					<th>Status</th>
					<th>Reference</th>
					<th>Linked advices</th>
				</tr>
			</thead>
			<tbody>
				<g:each in="${status.all}" var="measurementStatus">
					<tr>
						<td class="measurement-${measurements.get(measurementStatus.entity)?.derived ? 'derived' : 'provided'}">
							${measurementStatus.entity}
						</td>
						<td>
							${measurementStatus.value}
						</td>
						<td class="status color_${measurementStatus.color}">
							${measurementStatus.status}
						</td>
						<td>
							<g:if test="${references[measurementStatus.entity]}">
								<g:each in="${references[measurementStatus.entity]}" var="reference">
									<span class="status color_${reference.color}">
										${reference.status} 
										${reference.subjectCondition?.conditionDescription}
									</span>
								</g:each>
							</g:if>							
						</td>
							<g:if test="${measurementStatus.entity instanceof eu.qualify.food4me.Property}">${eu.qualify.food4me.decisiontree.Advice.findAllBySubject(measurementStatus.entity).code.unique().sort().join(', ')}</g:if>
						</td>
					</tr>
				</g:each>
			</tbody>
		</table>
		
	</body>
</html>
