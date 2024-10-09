package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.AbstractOptionSourceProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.*
import groovy.util.logging.Slf4j
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions
import com.morpheusdata.core.*
import com.morpheusdata.model.projection.ReferenceDataSyncProjection
import com.morpheusdata.model.ReferenceData


@Slf4j
class PuppetEnterpriseOptionSourceProvider extends AbstractOptionSourceProvider {

	Plugin plugin
	MorpheusContext morpheusContext
	HttpApiClient puppetEnterpriseAPI

	PuppetEnterpriseOptionSourceProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
		this.puppetEnterpriseAPI = new HttpApiClient()
	}

	@Override
	MorpheusContext getMorpheus() {
		return this.morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return this.plugin
	}

	@Override
	String getCode() {
		return 'puppet-enterprise-option-source'
	}

	@Override
	String getName() {
		return 'Puppet Enterprise Option Source'
	}

	@Override
	List<String> getMethodNames() {
		return new ArrayList<String>(['puppetEnterpriseAcountIntegrations','puppetEnterpriseNodeGroups','puppetEnterpriseBoltTasks','puppetEnterpriseBoltPlans','puppetEnterpriseBoltTimeoutCheck','puppetEnterpriseTagActions'])
	}

	def puppetEnterpriseAcountIntegrations(args){
		def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilter('type', 'puppet-enterprise-integration')).toList().blockingGet()
        def output = []
        for(integration in integrations){
            def dataMap = [:]
            dataMap["name"] = integration.name
            dataMap["value"] = integration.name
            output << dataMap
        }
		return output
	}

	def puppetEnterpriseNodeGroups(args){
		def integrationName = args["config"]["peIntegration"]
		def rtn = []
		def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", integrationName))).toList().blockingGet()
		// Handle an empty integration selection
		if (integrations.size > 0){
			def integrationId = integrations[0].id
			List<com.morpheusdata.model.ReferenceData> peReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.nodegroups.${integrationId}")).toList().blockingGet()
			peReferenceData.each { alrd ->
				def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
				if ( rd.config != null && !rd.config.isEmpty() ) {
					JsonSlurper slurper = new JsonSlurper()
					def json = slurper.parseText(rd.config)
					for (group in json.groups){
						def dataMap = [:]
						dataMap["name"] = group
						dataMap["value"] = group
						rtn << dataMap
					}
				}
			}
		}
		return rtn
	}

	def puppetEnterpriseBoltTasks(input){
		def params = [Object[]].any { it.isAssignableFrom(input.getClass()) } ? input.first() : input
		def rtn = []
		if (params.task?.taskOptions) {
			def integrationName = params.task.taskOptions.puppetEnterpriseIntegration
			def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", integrationName))).toList().blockingGet()
			def integrationId = integrations[0].id
			List<com.morpheusdata.model.ReferenceData> peReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.bolt.tasks.${integrationId}")).toList().blockingGet()
			def tasks = []

			peReferenceData.each { alrd ->
				def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
				if ( rd.config != null && !rd.config.isEmpty() ) {
					JsonSlurper slurper = new JsonSlurper()
					def json = slurper.parseText(rd.config)
					for (task in json.tasks){
						def dataMap = [:]
						dataMap["name"] = task
						dataMap["value"] = task
						tasks << dataMap
					}
				} else {
					def dataMap = [:]
					dataMap["name"] = rd.value
					dataMap["value"] = rd.value
					tasks << dataMap
				}
			}
			return tasks
		}
		return rtn
	}

	def puppetEnterpriseBoltPlans(input){
		def params = [Object[]].any { it.isAssignableFrom(input.getClass()) } ? input.first() : input
		def rtn = []
		if (params.task?.taskOptions) {
			def integrationName = params.task.taskOptions.puppetEnterpriseIntegrationBoltPlans
			def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", integrationName))).toList().blockingGet()
			def integrationId = integrations[0].id
			List<com.morpheusdata.model.ReferenceData> peReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.bolt.plans.${integrationId}")).toList().blockingGet()
			def plans = []

			peReferenceData.each { alrd ->
				def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
				if ( rd.config != null && !rd.config.isEmpty() ) {
					JsonSlurper slurper = new JsonSlurper()
					def json = slurper.parseText(rd.config)
					for (plan in json.plans){
						def dataMap = [:]
						dataMap["name"] = plan
						dataMap["value"] = plan
						plans << dataMap
					}
				} else {
					def dataMap = [:]
					dataMap["name"] = rd.value
					dataMap["value"] = rd.value
					plans << dataMap
				}
			}
			return plans
		}
		return rtn
	}

	def puppetEnterpriseTagActions(input){
		return [
				[name: 'Add', value: 'add'],
				[name: 'Remove', value: 'remove']
		]
	}
}