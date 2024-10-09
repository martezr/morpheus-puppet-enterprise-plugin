package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.AbstractTaskService
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.model.*
import groovy.util.logging.Slf4j
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions


@Slf4j
class PuppetEnterpriseTagNodeTaskService extends AbstractTaskService {
	MorpheusContext context

	PuppetEnterpriseTagNodeTaskService(MorpheusContext context) {
		this.context = context
	}

	@Override
	MorpheusContext getMorpheus() {
		return context
	}

	@Override
	TaskResult executeLocalTask(Task task, Map opts, Container container, ComputeServer server, Instance instance) {
		TaskConfig config = buildLocalTaskConfig([:], task, [], opts).blockingGet()

		if(instance) {
			config = buildInstanceTaskConfig(instance, [:], task, [], opts).blockingGet()
		}
		if(container) {
			config = buildContainerTaskConfig(container, [:], task, [], opts).blockingGet()
		}
	
        String peIntegration = task.taskOptions.find { it.optionType.code == 'puppetEnterpriseIntegration' }?.value
        String tagAction = task.taskOptions.find { it.optionType.code == 'puppetEnterpriseTagAction' }?.value

		def instanceId = instance.id

		if(tagAction == "add"){
			def taskAdds = []
			def configPayload = [:]
			configPayload["integration"] = peIntegration
			def configOutput = JsonOutput.toJson(configPayload)
			def tagMatch = context.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.instance.mapping.${instance.id}")
			])).blockingGet()
			// Check if there's an existing ref data entry
			if (tagMatch){
				tagMatch.config = configOutput
				context.async.referenceData.save(tagMatch).blockingGet()
			} else {	
				def variableConfig = [
					account: instance.account, 
					code: "puppetenterprise.instance.mapping.${instance.id}", 
					category:"puppetenterprise", 
					keyValue: "${instance.name}.${config.instance.domainName}", 
					description: "puppet enterprise instance tab",
					name: "instance-tab-ui", 
					value: "instance-tab-ui", 
					refType: 'Instance',
					type: 'string', 
					refId: instance.id,
					config: configOutput
				]
				def add = new ReferenceData(variableConfig)
				taskAdds << add
				context.referenceData.create(taskAdds).blockingGet()
			}
			return new TaskResult(
				success: true,
				data   : "Succesfully tagged the instance",
				output : "Succesfully tagged the instance"
			)
		}

 		if(tagAction == "remove"){
	        log.info "REMOVING REF DATA: ${instanceId}"
			def test = []
			def tasksMatch = context.async.referenceData.find(new DataQuery().withFilters([
				new DataFilter('code', "puppetenterprise.instance.mapping.${instance.id}")
			])).blockingGet()
			log.info "REF DATA ID: ${tasksMatch}"
			test << tasksMatch
			context.async.referenceData.remove(test).blockingGet()
			return new TaskResult(
				success: true,
				data   : "Succesfully removed the tag from the instance",
				output : "Succesfully removed the tag from the instance"
			)
		}
	}

	@Override
	TaskResult executeServerTask(ComputeServer server, Task task, Map opts) {
		TaskConfig config = buildComputeServerTaskConfig(server, [:], task, [], opts).blockingGet()
		context.executeCommandOnServer(server, 'echo $JAVA_HOME')
		context.executeCommandOnServer(server, 'echo $JAVA_HOME', false, 'user', 'password', null, null, null, false, false)
		executeTask(task, config)
	}

	@Override
	TaskResult executeServerTask(ComputeServer server, Task task) {
		TaskConfig config = buildComputeServerTaskConfig(server, [:], task, [], [:]).blockingGet()
		context.executeCommandOnServer(server, 'echo $JAVA_HOME')
		executeTask(task, config)
	}

	@Override
	TaskResult executeContainerTask(Container container, Task task, Map opts) {
		TaskConfig config = buildContainerTaskConfig(container, [:], task, [], opts).blockingGet()
		context.executeCommandOnWorkload(container, 'echo $JAVA_HOME')
		context.executeCommandOnWorkload(container, 'echo $JAVA_HOME', 'user', 'password', null, null, null, false, null, false)
		executeTask(task, config)
	}

	@Override
	TaskResult executeContainerTask(Container container, Task task) {
		TaskConfig config = buildContainerTaskConfig(container, [:], task, [], [:]).blockingGet()
		executeTask(task, config)
	}

	@Override
	TaskResult executeRemoteTask(Task task, Map opts, Container container, ComputeServer server, Instance instance) {
		TaskConfig config = buildRemoteTaskConfig([:], task, [], opts).blockingGet()
		context.executeCommandOnWorkload(container, 'echo $JAVA_HOME')
		executeTask(task, config)
	}

	@Override
	TaskResult executeRemoteTask(Task task, Container container, ComputeServer server, Instance instance) {
		TaskConfig config = buildRemoteTaskConfig([:], task, [], [:]).blockingGet()
		context.executeSshCommand('localhost', 8080, 'bob', 'password', 'echo $JAVA_HOME', null, null, null, false, null, LogLevel.debug, false, null, true)
		executeTask(task, config)
	}

	/**
	 * Finds the input text from the OptionType created in {@link ReverseTextTaskProvider#getOptionTypes}.
	 * Uses Groovy {@link org.codehaus.groovy.runtime.StringGroovyMethods#reverse} on the input text
	 * @param task
	 * @param config
	 * @return data and output are the reversed text
	 */
	TaskResult executeTask(Task task, TaskConfig config) {
		println config.accountId
		def taskOption = task.taskOptions.find { it.optionType.code == 'reverseTextTaskText' }
		String data = taskOption?.value?.reverse()
		new TaskResult(
				success: true,
				data   : data,
				output : data
		)
	}
}