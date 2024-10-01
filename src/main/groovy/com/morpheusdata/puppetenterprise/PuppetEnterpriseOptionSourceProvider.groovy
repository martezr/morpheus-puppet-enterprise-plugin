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
		return new ArrayList<String>(['puppetEnterpriseAcountIntegrations','puppetEnterpriseNodeGroups','puppetEnterpriseBoltTasks','puppetEnterpriseBoltPlans','puppetEnterpriseBoltTimeoutCheck'])
	}

	def puppetEnterpriseAcountIntegrations(args){
        log.info "OP ARG: ${args}"
		def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilter('type', 'puppet-enterprise-integration')).toList().blockingGet()
        log.info "Integrations: ${integrations}"
        def output = []
        for(integration in integrations){
            def test = [:]
            test["name"] = integration.name
            test["value"] = integration.name
            output << test
        }
		return output
	}

	def puppetEnterpriseNodeGroups(args){
        log.info "WORKFLOW TEMPLATE ARGs: ${args}"
		def integrationName = args["config"]["peIntegration"]
		log.info "INTEGRATION PE SERVER ${integrationName}"

		return [
				[name: 'All Nodes', value: 'all nodes'],
				[name: 'PE Infrastructure', value: 'pe infrastructure'],
				[name: 'PE Patch Management', value: 'sso']
		]
	}

	def puppetEnterpriseBoltTimeoutCheck(input){
		return [
				[name: 'Yes', value: 'yes'],
				[name: 'No', value: 'no']
		]
	}

	def puppetEnterpriseBoltTasks(input){
		def params = [Object[]].any { it.isAssignableFrom(input.getClass()) } ? input.first() : input
		log.info "BOLT TASK ARGs: ${params}"
		def rtn = []
		if (params.task?.taskOptions) {
			log.info params.task.taskOptions.puppetEnterpriseIntegration
			def integrationName = params.task.taskOptions.puppetEnterpriseIntegration
			def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", integrationName))).toList().blockingGet()
			def integrationId = integrations[0].id
			log.info "BINTS: ${integrations[0]}"
			List<com.morpheusdata.model.ReferenceData> peReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.bolt.tasks.${integrationId}")).toList().blockingGet()
			def tasks = []

			peReferenceData.each { alrd ->
				def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
				log.info "Config ${rd}"
				log.info "Integration: ${rd.config}"
				if ( rd.config != null && !rd.config.isEmpty() ) {
					JsonSlurper slurper = new JsonSlurper()
					def json = slurper.parseText(rd.config)
					log.info "JSON PAYLOAD: ${json}"
					for (task in json.tasks){
						def bt = [:]
						bt["name"] = task
						bt["value"] = task
						tasks << bt
					}
				} else {
					def bt = [:]
					bt["name"] = rd.value
					bt["value"] = rd.value
					tasks << bt
				}
			}

			return tasks
		}
		return [
				[name: 'server', value: 'server'],
				[name: 'client', value: 'client'],
				[name: 'sso', value: 'sso']
		]

	}


	def puppetEnterpriseBoltPlans(input){
		log.info "BOLT Plan inputs: ${input}"
		def params = [Object[]].any { it.isAssignableFrom(input.getClass()) } ? input.first() : input
		log.info "BOLT Plan ARGs: ${params}"
		def rtn = []
		if (params.task?.taskOptions) {
			log.info params.task.taskOptions.puppetEnterpriseIntegrationBoltPlans
			def integrationName = params.task.taskOptions.puppetEnterpriseIntegrationBoltPlans
			def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", integrationName))).toList().blockingGet()
			def integrationId = integrations[0].id
			List<com.morpheusdata.model.ReferenceData> peReferenceData = morpheusContext.referenceData.list(new DataQuery().withFilter("code", "puppetenterprise.bolt.plans.${integrationId}")).toList().blockingGet()
			def plans = []

			peReferenceData.each { alrd ->
				def rd = morpheusContext.referenceData.get(alrd.id)?.blockingGet()
				log.info "Config ${rd}"
				log.info "Integration: ${rd.config}"
				if ( rd.config != null && !rd.config.isEmpty() ) {
					JsonSlurper slurper = new JsonSlurper()
					def json = slurper.parseText(rd.config)
					log.info "JSON PAYLOAD: ${json}"
					for (plan in json.plans){
						def bt = [:]
						bt["name"] = plan
						bt["value"] = plan
						plans << bt
					}
				} else {
					def bt = [:]
					bt["name"] = rd.value
					bt["value"] = rd.value
					plans << bt
				}
			}

			return plans
		}
		return rtn

	}
}