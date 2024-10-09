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
import com.morpheusdata.model.Container
import com.morpheusdata.model.ComputeServer

class PuppetEnterpriseTagNodeTaskProvider implements TaskProvider {
	MorpheusContext morpheusContext
	Plugin plugin
	AbstractTaskService service

	PuppetEnterpriseTagNodeTaskProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.plugin = plugin
		this.morpheusContext = morpheusContext
	}

	@Override
	ExecutableTaskInterface getService() {
		return new PuppetEnterpriseTagNodeTaskService(morpheus)
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
		return "puppet-enterprise-tag-node-task"
	}

	@Override
	TaskType.TaskScope getScope() {
		return TaskType.TaskScope.all
	}

	@Override
	String getName() {
		return 'Puppet Enterprise Tag Node'
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
		OptionType puppetEnterpriseIntegration = new OptionType(
			name: 'Puppet Enterprise Integration',
			code: 'puppetEnterpriseIntegration',
			fieldName: 'puppetEnterpriseIntegration',
			displayOrder: 0,
			fieldLabel: 'Puppet Enterprise Integration',
			required: true,
			noSelection: false,
			inputType : OptionType.InputType.SELECT,
			optionSource: 'puppetEnterpriseAcountIntegrations'
		)
		OptionType puppetEnterpriseTagAction = new OptionType(
			name: 'Puppet Enterprise Tag Action',
			code: 'puppetEnterpriseTagAction',
			fieldName: 'puppetEnterpriseTagAction',
			displayOrder: 1,
			fieldLabel: 'Action',
			required: true,
			noSelection: false,
			inputType : OptionType.InputType.SELECT,
			optionSource: 'puppetEnterpriseTagActions'
		)
		return [puppetEnterpriseIntegration, puppetEnterpriseTagAction]
	}

	@Override
	Icon getIcon() {
		return new Icon(path:"puppet-enterprise.png", darkPath: "puppet-enterprise.png")
	}

	@Override
	TaskResult executeLocalTask(Task task, Map map, Workload workload, ComputeServer computeServer, Instance instance) {
		return null
	}

	@Override
	TaskResult executeServerTask(ComputeServer server, Task task, Map opts) {
		return null
	}

	@Override
	TaskResult executeServerTask(ComputeServer server, Task task) {
		return null
	}

	@Override
	TaskResult executeContainerTask(Workload workload, Task task, Map opts) {
		return null
	}

	@Override
	TaskResult executeContainerTask(Workload workload, Task task) {
		return null
	}

	@Override
	TaskResult executeRemoteTask(Task task, Map opts, Workload workload, ComputeServer server, Instance instance) {
		return null
	}

	@Override
	TaskResult executeRemoteTask(Task task, Workload workload, ComputeServer server, Instance instance) {
		return null
	}

	/**
	 * Finds the input text from the OptionType created in {@link ReverseTextTaskProvider#getOptionTypes}.
	 * Uses Groovy {@link org.codehaus.groovy.runtime.StringGroovyMethods#reverse} on the input text
	 * @param task
	 * @param config
	 * @return data and output are the reversed text
	 */
	TaskResult executeTask(Task task, TaskConfig config) {
		def data = "Task Test"
		new TaskResult(
				success: true,
				data   : data,
				output : data
		)
	}
}