package com.ngweb.web.springmvc.grails

import java.lang.reflect.Modifier

import java.util.Map

import groovy.util.GroovyScriptEngine

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.commons.lang.StringUtils

import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.ui.Model
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UrlPathHelper

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.DefaultGrailsUrlMappingsClass
import org.codehaus.groovy.grails.commons.metaclass.MetaClassEnhancer
import org.codehaus.groovy.grails.plugins.web.api.ControllersApi
import org.codehaus.groovy.grails.web.mapping.*
import org.codehaus.groovy.grails.web.metaclass.RedirectDynamicMethod
import org.codehaus.groovy.grails.web.plugins.support.WebMetaUtils
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest

import com.google.common.base.CaseFormat

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GrailsControllerAdapter 
{	
	GrailsControllerAdapter(Map options, ApplicationContext applicationContext) 
	{				
		// Remove both leading and trailing slashes
		mPathPrefix = StringUtils.chomp(
			StringUtils.removeStart(options.pathPrefix, "/"), "/")
		
		// Convert "prefix/" to "/prefix", leaving the empty string alone				
		mLeadingSlashPathPrefix = StringUtils.chomp("/" + mPathPrefix, "/")		

		mViewPathPrefixToStrip = options.viewPathPrefixToStrip
		mPathSuffixToStrip = options.pathSuffixToStrip
		mUrlCaseFormat = options.urlCaseFormat
		
		mDevelopmentMode = (options.developmentMode ?: false)
		mSourcePaths = options.sourcePaths
		
		mUrlMappingsClassName = options.urlMappingsClassName
		mMatchAllPatterns = options.matchAllPatterns ?: false
		
		mPackagePrefix = StringUtils.trimToEmpty(options.controllerPackageName)
		
		if (!mPackagePrefix.isEmpty())
		{
			mPackagePrefix += '.'
		}	
		
		mApplicationContext = applicationContext			
	}
	
	def handleRequest(
		HttpServletRequest request, 
		HttpServletResponse response, 
		Model model,
		ServletContext servletContext
  ) 
	{		
		def grailsWebRequest = new GrailsWebRequest(request, response,
			servletContext, mApplicationContext)							
		
		RCH.setRequestAttributes(grailsWebRequest)

		/*
		mLogger.info("new params = " +  RCH.currentRequestAttributes().params)
		mLogger.info("new flash = " + RCH.currentRequestAttributes().flashScope) */
				
		UrlMappingsHolder urlMappingsHolder = null
				
		GroovyScriptEngine groovyScriptEngine = null		
					
		if (mCachedUrlMappingsHolder == null)
		{
			groovyScriptEngine = makeGroovyScriptEngine()
			
			def urlMappingsClass = loadClass(mUrlMappingsClassName, 
				groovyScriptEngine)

			urlMappingsHolder = makeUrlMappingsHolder(urlMappingsClass)
			
			if (!mDevelopmentMode)
			{				
				mCachedUrlMappingsHolder = urlMappingsHolder
			}
		} 
		else  
		{
			urlMappingsHolder = mCachedUrlMappingsHolder
		}
		
		def uriToMatch = computeUriToMatch(request) 		

		def urlMatch = matchUri(uriToMatch, request.method, urlMappingsHolder)
													
		if (urlMatch == null) {
			mLogger.warn("no url matches for " + uriToMatch)
			response.sendError(404)
			return null
		}
		
		def explicitUri = urlMatch.getURI()
		 
		// Needs test
		if (explicitUri != null) {
			// Need to convert from GString to ordinary Java string
			return "redirect:${explicitUri}".toString() 
		} 
		
		if (urlMatch.viewName != null) 
		{
			return urlMatch.viewName.toString()
		}		
		
		urlMatch.configure(grailsWebRequest)
		
		String controllerName = mUrlCaseFormat.to(CaseFormat.UPPER_CAMEL,
			urlMatch.controllerName) + "Controller"
				
		if (mLogger.isDebugEnabled())
		{										
			mLogger.debug("using controller " + controllerName)
		}
		
		def controllerClass = loadClass(mPackagePrefix + controllerName, 
			groovyScriptEngine) 
								
		def controller = controllerClass.newInstance() 					

		enhanceController(controller, model)

		def actionName = computeActionName(urlMatch, controller, controllerName) 
	
		if (mLogger.isDebugEnabled())
		{
			mLogger.debug("using action " + actionName)
		}
									
		if (actionName == null)
		{
			if (mLogger.isDebugEnabled())
			{
				mLogger.debug("no action found, returning default view " + uriToMatch)
			}

			// No action defined, just use default view			
			String rv = mLeadingSlashPathPrefix + uriToMatch
			
			if (rv.endsWith("/")) 
			{
				rv += "index"
			}			
			
			return rv
		} 
		else 
		{
		  def rv = controller."${actionName}"()
	
			if (mLogger.isDebugEnabled()) 
			{			 		
				mLogger.debug("controller returned " + rv)
			}

			if (rv == null)
			{			
				return controller.modelAndView
			} else if ((rv instanceof String) || (rv instanceof Map)) {
				return rv;
			}

			return rv			
		}
	}
		
	/** Create a script engine only if necessary. */
	private GroovyScriptEngine makeGroovyScriptEngine() 
	{
		if (mDevelopmentMode) 
		{		
			return new GroovyScriptEngine(mSourcePaths)
		}
		
		return null
	}
	
	private Class loadClass(
		String className, 
		GroovyScriptEngine gse
  )  
	{
		if (gse == null)
		{
			return getClass().classLoader.loadClass(className, true)
		} 

		String pathName = className.replace('.' as char, '/' as char)
		
		gse.loadScriptByName(pathName + ".groovy")						
	} 
	
	private String computeUriToMatch( 
		HttpServletRequest request
  )
	{		
		def urlPathHelper = new UrlPathHelper()
		
		StringUtils.removeEnd(StringUtils.removeStart(
			urlPathHelper.getPathWithinApplication(request), mLeadingSlashPathPrefix), 
		  mPathSuffixToStrip)
	}
	
	private UrlMappingsHolder makeUrlMappingsHolder(Class urlMappingsClass) {
		def evaluator = new DefaultUrlMappingEvaluator(mApplicationContext)
		
		def mappingClass = new DefaultGrailsUrlMappingsClass(urlMappingsClass)
		
		def urlMappingsList = evaluator.evaluateMappings(mappingClass.mappingsClosure)
		
		new DefaultUrlMappingsHolder(urlMappingsList)
	}
	
	private def matchUri(
		String uri,
		String methodName,
		UrlMappingsHolder urlMappingsHolder
	) 
	{			
		if (mMatchAllPatterns) 
		{	 				
			UrlMappingInfo[] urlMatches = urlMappingsHolder.matchAll(uri, methodName)
		
			if (urlMatches.length == 0)
			{
				return null
			}

			return urlMatches[0]
		}
		
		return urlMappingsHolder.match(uri)
					
	}	
		
	private String computeActionName(
		UrlMappingInfo urlMatch, 
		Object controller,
		String controllerName) 
	{	
		String actionName = urlMatch.actionName
		
		if (actionName == null) 
		{
			if (controller.metaClass.getMetaProperty("defaultAction"))
			{
				actionName = controller.defaultAction
			}
		}
						
		if (actionName == null) 
		{
			
			/* We get methods from Object like wait() that match. So the 
			number of methods is always > 1 and this is not a good criteria.
			
			boolean foundIndex = false
			int numMethods = 0
			String lastMethodName = null
						
			def methods = controller.metaClass.methods.each {
				method ->
				if (Modifier.isPublic(method.modifiers) &&
						!Modifier.isAbstract(method.modifiers) &&
				    (method.parameterTypes.length == 0)) 
				{
					if (mLogger.isTraceEnabled()) 
					{
						mLogger.trace("Matching method " + method.name)
					}
					
					String name = method.name
					
					if (name == "index") {
						foundIndex = true
					}
					numMethods++						
					lastMethodName = name				
				} else {
					if (mLogger.isTraceEnabled())
					{
						mLogger.debug("Non matching method " + method.name)
					}
				}																
			}
			
			if (foundIndex) {
				actionName = "index"
			} 
			else if (numMethods == 1) {
				actionName = lastMethodName
			} */
			
			if (controller.metaClass.getMetaMethod("index")) 
			{
				actionName = "index"
			}			
		}
		
		if (actionName == null) {
			return null
		}						
			
		mUrlCaseFormat.to(CaseFormat.LOWER_CAMEL, actionName)
	}
		
	private def enhanceController(Object controller, 
		Model model) 
	{
		WebMetaUtils.registerCommonWebProperties(
			controller.metaClass, mApplicationContext.getBean(GrailsApplication.class))
					
		controller.metaClass.getModel = {
			model.asMap()
		}

		controller.metaClass.getApplicationContext = {
			mApplicationContext
		}
		
		controller.metaClass.getPathPrefix = {
			mPathPrefix
		}

		controller.metaClass.getViewPathPrefixToStrip = {
			mViewPathPrefixToStrip
		}
		
		controller.metaClass.getPathSuffixToStrip = {
			mPathSuffixToStrip
		}
		
		// Add the render(), redirect(), forward(), etc. methods to the controller
		def controllersApi = new ControllersApi()
	
		def enhancer = new MetaClassEnhancer()
		enhancer.addApi(controllersApi)
		enhancer.enhance(controller.metaClass)
							
		mApplicationContext.autowireCapableBeanFactory.autowireBeanProperties(
			controller, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
	}	
	
	private ApplicationContext mApplicationContext = null
	private String mPathPrefix = null
	private String mLeadingSlashPathPrefix = null
	private String mViewPathPrefixToStrip = null
	private String mPathSuffixToStrip = null
	private CaseFormat mUrlCaseFormat = null
	private boolean mDevelopmentMode = false
	private boolean mMatchAllPatterns = false
	private String[] mSourcePaths = null 
	private String mUrlMappingsClassName = null
	private String mPackagePrefix = null
	private UrlMappingsHolder mCachedUrlMappingsHolder = null
			
	private Logger mLogger = LoggerFactory.getLogger(getClass().getName())
}