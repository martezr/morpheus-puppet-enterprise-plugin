package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.HttpApiClient.RequestOptions
import groovy.sql.GroovyRowResult
import groovy.util.logging.Slf4j

@Slf4j
class PuppetEnterpriseReportProvider extends AbstractReportProvider{
	protected MorpheusContext morpheusContext
	protected Plugin plugin

	PuppetEnterpriseReportProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.morpheusContext = morpheusContext
		this.plugin = plugin
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
		return "morpheus-puppet-enterprise-run-report"
	}

	@Override
	String getName() {
		return "Puppet Enterprise Run Report"
	}

	@Override
	ServiceResponse validateOptions(Map opts) {
		return ServiceResponse.success()
	}

	@Override
	void process(ReportResult reportResult) {
		// Update the status of the report (generating) - https://developer.morpheusdata.com/api/com/morpheusdata/model/ReportResult.Status.html
		morpheus.async.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingAwait();
		Long displayOrder = 0
		List<GroovyRowResult> results = []

		Long unchanged = 0
		Long changed = 0
		Long above30s = 0
		Long above5m = 0
		Long above10m = 0
		Long above30m = 0

		def integrations = morpheusContext.async.accountIntegration.list(new DataQuery().withFilters(new DataFilter("type", "puppet-enterprise-integration"), new DataFilter("name", reportResult.configMap?.peIntegration))).toList().blockingGet()
		def integrationName = reportResult.configMap?.peIntegration

		// Set Puppet Enterprise integration variables
		def accessToken = ""
		def serviceUrl = ""
		def ignoreVerifySSL = ""
		def orchestratorPort = ""
		def puppetDbPort = ""
		def useCache = ""
		
        for(integration in integrations){
			if (integration.name == integrationName){
				JsonSlurper slurper = new JsonSlurper()
				def integrationJson = slurper.parseText(integration.config)
				accessToken = integrationJson.cm.plugin.serviceToken
				serviceUrl = integrationJson.cm.plugin.serviceUrl
				ignoreVerifySSL = integrationJson.cm.plugin.ignoreVerifySSL
				orchestratorPort = integrationJson.cm.plugin.peOrchestratorPort
				puppetDbPort = integrationJson.cm.plugin.puppetdbPort
				useCache = integrationJson.cm.plugin.peDataCache
			}
		}

        def queryPayload = [:]
        queryPayload["query"] = ["=","latest_report?","true"]
		HttpApiClient client = new HttpApiClient()
		HttpApiClient.RequestOptions requestOptions = new HttpApiClient.RequestOptions(headers:['X-Authentication':accessToken,'Content-Type':'application/json'],ignoreSSL: ignoreVerifySSL, body: queryPayload)
		def apiresults = client.callJsonApi("${serviceUrl}:${puppetDbPort}", "pdb/query/v4/reports", "", "", requestOptions, 'POST')
		def json = apiresults.data
        json.each{
			def status = it.status
			switch(status) {
				case "changed":
					changed++
					break;
				case "unchanged":
					unchanged++
					break;
				default:
				    break;
			}

			def metricData = it.metrics.data
			def runTime = ""
			metricData.each{
				if (it["name"] == "total" && it["category"] == "time"){
					runTime = it["value"]
					if (runTime<300) { 
						log.info("GREATER THAN 30S")
						above30s++
					} else if (runTime<600) { 
						log.info("GREATER THAN 5m")
						above5m++
					}
				}
			}
			Map<String,Object> data = [name: it.certname, status: it.status, version: it.puppet_version, environment: it.environment, reporttime: it.receive_time, runtime: runTime ]
			ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
			morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()
        }

		Map<String,Object> data = [unchanged: unchanged, changed: changed, above30s: above30s, above5m: above5m ]
		ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_HEADER, displayOrder: displayOrder++, dataMap: data)
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()

        morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingAwait();
		client.shutdownClient()
	}

	@Override
	String getDescription() {
		return "View an inventory of Puppet Enterprise agent run reports"
	}

	/**
	 * Gets the category string for the report. Reports can be organized by category when viewing.
	 * @return the category string (i.e. inventory)
	 */
	@Override
	String getCategory() {
		return "inventory"
	}

	/**
	 * Only the owner of the report result can view the results.
	 * @return whether this report type can be read by the owning user only or not
	 */
	@Override
	Boolean getOwnerOnly() {
		return false
	}

	/**
	 * Some reports can only be run by the master tenant for security reasons. This informs Morpheus that the report type
	 * is a master tenant only report.
	 * @return whether or not this report is for the master tenant only.
	 */
	@Override
	Boolean getMasterOnly() {
		return false
	}

	@Override
	Boolean getSupportsAllZoneTypes() {
		return true
	}

	@Override
	List<OptionType> getOptionTypes() {
		  OptionType peIntegrations = new OptionType(
			code: 'peIntegration', 
			name: 'Puppet Enterprise Server', 	
			inputType: 'SELECT', 
			optionSource: 'puppetEnterpriseAcountIntegrations', 
			fieldName: 'peIntegration',
			fieldContext: 'config',
			fieldLabel: 'Integration',
			required: true,
			displayOrder: 0
		  )
		  OptionType peNodeGroups = new OptionType(
			code: 'peNodeGroup', 
			name: 'Puppet Enterprise Node Groups', 	
			inputType: 'MULTI_SELECT', 
			optionSource: 'puppetEnterpriseNodeGroups', 
			fieldName: 'peNodeGroup', 
			fieldLabel: 'Node Group',
			fieldContext: 'config',
			dependsOn: 'peIntegration',
			displayOrder: 1
		  )

		return [peIntegrations, peNodeGroups]
	}

	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<Map<String, List<ReportResultRow>>> model = new ViewModel<>()
		def HashMap<String, String> reportPayload = new HashMap<String, String>();
		def webnonce = morpheus.getWebRequest().getNonceToken()
		reportPayload.put("webnonce",webnonce)
		def integrationName = reportResult.configMap?.peIntegration
		def nodeGroups = reportResult.configMap?.peNodeGroup
		reportPayload.put("integration",integrationName)
		reportPayload.put("nodeGroups",nodeGroups)
		reportPayload.put("reportdata",reportRowsBySection)
		model.object = reportPayload
		getRenderer().renderTemplate("hbs/puppetEnterpriseReport", model)
	}
}
