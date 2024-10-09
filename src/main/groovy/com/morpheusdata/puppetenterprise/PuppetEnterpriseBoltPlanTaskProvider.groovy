package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.*
import com.morpheusdata.core.providers.TaskProvider
import com.morpheusdata.model.Icon
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.TaskType
import com.morpheusdata.model.TaskResult
import com.morpheusdata.model.TaskConfig
import com.morpheusdata.model.Task
import com.morpheusdata.model.Workload
import com.morpheusdata.model.Instance
import com.morpheusdata.model.ComputeServer

class PuppetEnterpriseBoltPlanTaskProvider implements TaskProvider {
	MorpheusContext morpheusContext
	Plugin plugin
	AbstractTaskService service

	PuppetEnterpriseBoltPlanTaskProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	@Override
	ExecutableTaskInterface getService() {
		return new PuppetEnterpriseBoltPlanTaskService(morpheus)
	}

	@Override
	MorpheusContext getMorpheus() {
		return morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return plugin
	}

	@Override
	String getCode() {
		return "puppet-enterprise-bolt-plan"
	}

	@Override
	TaskType.TaskScope getScope() {
		return TaskType.TaskScope.all
	}

	@Override
	String getName() {
		return 'Bolt Plan'
	}

	@Override
	String getDescription() {
		return 'A custom task that runs in docker'
	}

	@Override
	Boolean isAllowExecuteLocal() {
		return true
	}

	@Override
	Boolean isAllowExecuteRemote() {
		return false
	}

	@Override
	Boolean isAllowExecuteResource() {
		return false
	}

	@Override
	Boolean isAllowLocalRepo() {
		return false
	}

	@Override
	Boolean isAllowRemoteKeyAuth() {
		return false
	}

	@Override
	Boolean hasResults() {
		return true
	}

	@Override
	List<OptionType> getOptionTypes() {
		OptionType puppetEnterpriseIntegrationBoltPlans = new OptionType(
			name: 'Puppet Enterprise Integration Bolt Plans',
			code: 'puppetEnterpriseIntegrationBoltPlans',
			fieldName: 'puppetEnterpriseIntegrationBoltPlans',
			displayOrder: 0,
			fieldLabel: 'Puppet Enterprise Integration',
			required: true,
			noSelection: false,
			inputType : OptionType.InputType.SELECT,
			optionSource: 'puppetEnterpriseAcountIntegrations'
		)
		OptionType boltPlan = new OptionType(
			name: 'Bolt Plan',
			code: 'boltPlan',
			fieldName: 'boltPlan',
			displayOrder: 1,
			fieldLabel: 'Bolt Plan',
			required: true,
			dependsOn: 'puppetEnterpriseIntegrationBoltPlans',
			inputType : OptionType.InputType.SELECT,
			optionSource: 'puppetEnterpriseBoltPlans'
		)
		OptionType boltPlanJobDescription = new OptionType(
			name: 'Bolt Plan Job Description',
			code: 'boltPlanJobDescription',
			fieldName: 'boltPlanJobDescription',
			displayOrder: 2,
			fieldLabel: 'Job Description',
			inputType : OptionType.InputType.TEXT,
		)
		OptionType boltPlanParams = new OptionType(
			name: 'Bolt Plan Parms',
			code: 'boltPlanParams',
			fieldName: 'boltPlanParams',
			displayOrder: 3,
			defaultValue: '{}',
			fieldLabel: 'Bolt Plan Parameters',
			required: true,
			inputType : OptionType.InputType.CODE_EDITOR,
		)
		return [puppetEnterpriseIntegrationBoltPlans, boltPlan, boltPlanJobDescription, boltPlanParams]
	}

	@Override
	Icon getIcon() {
		return new Icon(path:"puppet-enterprise.png", darkPath: "puppet-enterprise.png")
	}

	@Override
	TaskResult executeLocalTask(Task task, Map opts, Workload workload, ComputeServer server, Instance instance) {
		TaskConfig config = buildLocalTaskConfig([:], task, [], opts).blockingGet()
		if(instance) {
			config = buildInstanceTaskConfig(instance, [:], task, [], opts).blockingGet()
		}
		if(workload) {
			config = buildContainerTaskConfig(workload, [:], task, [], opts).blockingGet()
		}
	
		executeTask(task, config)
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
	TaskResult executeContainerTask(Workload workload, Task task, Map opts) {
		TaskConfig config = buildContainerTaskConfig(workload, [:], task, [], opts).blockingGet()
		context.executeCommandOnWorkload(workload, 'echo $JAVA_HOME')
		context.executeCommandOnWorkload(workload, 'echo $JAVA_HOME', 'user', 'password', null, null, null, false, null, false)
		executeTask(task, config)
	}

	@Override
	TaskResult executeContainerTask(Workload workload, Task task) {
		TaskConfig config = buildContainerTaskConfig(workload, [:], task, [], [:]).blockingGet()
		executeTask(task, config)
	}

	@Override
	TaskResult executeRemoteTask(Task task, Map opts, Workload workload, ComputeServer server, Instance instance) {
		TaskConfig config = buildRemoteTaskConfig([:], task, [], opts).blockingGet()
		context.executeCommandOnWorkload(container, 'echo $JAVA_HOME')
		executeTask(task, config)
	}

	@Override
	TaskResult executeRemoteTask(Task task, Workload workload, ComputeServer server, Instance instance) {
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
		//def taskOption = task.taskOptions.find { it.optionType.code == 'reverseTextTaskText' }
        //println taskOption?.value?
		def data = "Task Test"
		new TaskResult(
				success: true,
				data   : data,
				output : data
		)
	}
}