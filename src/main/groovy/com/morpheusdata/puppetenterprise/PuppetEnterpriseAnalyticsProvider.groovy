package com.morpheusdata.puppetenterprise

import com.morpheusdata.core.providers.AbstractAnalyticsProvider;
import com.morpheusdata.core.MorpheusContext;
import groovy.util.logging.Slf4j
import com.morpheusdata.model.User
import com.morpheusdata.core.Plugin;
import com.morpheusdata.response.ServiceResponse;
import com.morpheusdata.views.HTMLResponse;
import com.morpheusdata.views.ViewModel;
import java.util.Map;
import java.util.LinkedHashMap;

@Slf4j
class PuppetEnterpriseAnalyticsProvider extends AbstractAnalyticsProvider{
	protected MorpheusContext morpheusContext
	protected Plugin plugin

	PuppetEnterpriseAnalyticsProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.morpheusContext = morpheusContext;
		this.plugin = plugin;
	}

	/**
	 * Returns the Morpheus Context for interacting with data stored in the Main Morpheus Application
	 *
	 * @return an implementation of the MorpheusContext for running Future based rxJava queries
	 */
	@Override
	MorpheusContext getMorpheus() {
		return this.morpheusContext;
	}

	/**
	 * Returns the instance of the Plugin class that this provider is loaded from
	 * @return Plugin class contains references to other providers
	 */
	@Override
	Plugin getPlugin() {
		return this.plugin;
	}

	/**
	 * A unique shortcode used for referencing the provided provider. Make sure this is going to be unique as any data
	 * that is seeded or generated related to this provider will reference it by this code.
	 * @return short code string that should be unique across all other plugin implementations.
	 */
	@Override
	String getCode() {
		return "bolt.plan";
	}

	/**
	 * Provides the provider name for reference when adding to the Morpheus Orchestrator
	 * NOTE: This may be useful to set as an i18n key for UI reference and localization support.
	 *
	 * @return either an English name of a Provider or an i18n based key that can be scanned for in a properties file.
	 */
	@Override
	String getName() {
		return "Bolt Plan Analytics";
	}

	/**
	 * A short description of the analytics for the user to better understand its purpose.
	 * @return the description string
	 */
	@Override
	String getDescription() {
		return "TODO: Enter a Description for your Analytics Type Here";
	}

	/**
	 * Gets the category string for the analytics. Analytics can be organized by category when viewing.
	 * @return the category string (i.e. inventory)
	 */
	@Override
	String getCategory() {
		return "puppet enterprise";
	}

	/**
	 * Is this analytics page reserved for the master tenant only. This is used to restrict access to the analytics page to only the master tenant.
	 * @return the master tenant only flag
	 */
	Boolean getMasterTenantOnly() {
		return false;
	}

	/**
	 * Is this analytics page reserved for sub tenants only. This is used to restrict access to the analytics page to only sub tenants.
	 * @return the sub tenant only flag
	 */
	Boolean getSubTenantOnly() {
		return false;
	}

	/**
	 * The order within a category the analytics provider should be displayed. This is used to order analytics providers in the UI.
	 * @return the display order starting from 0 as highest priority
	 */
	Integer getDisplayOrder() {
		return 0;
	}


	/**
	 * Load data for the analytics page. This method should return a map of data that can be used to render the analytics page.
	 * @param user the current user viewing the page
	 * @param params the parameters passed to the analytics page
	 * @return a map of data to be used in the analytics page
	 */
	public ServiceResponse<Map<String,Object>> loadData(User user, Map<String, Object> params) {
		def response = new ServiceResponse()
		log.info "ANALYTICS DATA PARAMS: ${params}"
		LinkedHashMap<String, Object> rtn = new LinkedHashMap<>();
		def ml = [[id:9, status:"Stopped", user:"admin", nodes: 2], [id:8, status:"Stopped", user:"admin", nodes: 5]]
		rtn["jobs"] = ml
		response["data"] = rtn
		return ServiceResponse.success(rtn)
	}

	/**
	 * Presents the HTML Rendered output of a analytics. This can use different {@link Renderer} implementations.
	 * The preferred is to use server side handlebars rendering with {@link com.morpheusdata.views.HandlebarsRenderer}
	 * <p><strong>Example Render:</strong></p>
	 * <pre>{@code
	 *    ViewModel model = new ViewModel()
	 * 	  model.object = analyticsRowsBySection
	 * 	  getRenderer().renderTemplate("hbs/instanceAnalytics", model)
	 *}</pre>
	 * @param analyticsResult the results of a analytics
	 * @param analyticsRowsBySection the individual row results by section (i.e. header, vs. data)
	 * @return result of rendering an template
	 */
	@Override
	HTMLResponse renderTemplate(User user, Map<String, Object> data, Map<String, Object> params) {
		ViewModel<Map<String, Object>> model = new ViewModel<>();
		log.info "ANALYTICS DATA RENDER: ${data}"
		log.info "ANALYTICS PARAMS RENDER: ${params}"
		def HashMap<String, String> analyticsPayload = new HashMap<String, String>();
		analyticsPayload.put("data", data)
		def webnonce = morpheus.getWebRequest().getNonceToken()
		analyticsPayload.put("webnonce",webnonce)
		model.object = analyticsPayload;
		getRenderer().renderTemplate("hbs/puppetEnterpriseAnalytics", model);
	}
}