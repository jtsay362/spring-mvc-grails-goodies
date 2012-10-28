package com.ngweb.web.springmvc.grails

import java.lang.{Boolean => JBoolean}
import java.util.{List => JList}
import java.util.{Map => JMap, HashMap => JHashMap}
import java.util.Locale

import scala.reflect.BeanProperty

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.commons.lang.StringUtils 

import com.google.common.base.CaseFormat

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationContext
import org.springframework.ui.Model
import org.springframework.ui.ExtendedModelMap
import org.springframework.web.context.ServletContextAware
import org.springframework.web.servlet.mvc.Controller 
import org.springframework.web.servlet._
import org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator

import org.codehaus.groovy.grails.plugins.web.ServletsGrailsPluginSupport

class GrailsLikeController 
extends Controller with InitializingBean with ApplicationContextAware 
with ServletContextAware 
{      
  import GrailsLikeController._
      
  override def 
  afterPropertiesSet() : Unit = 
  {
    require(pathPrefix != null)
    require(pathSuffixToStrip != null)
    require(urlCaseFormat != null)
    require(sourcePaths != null)
  	require(controllerPackageName != null)
  	require(applicationContext != null)
  	require(servletContext != null)
  	
  	if (developmentMode.booleanValue())
  	{
  	  require(sourcePaths.length > 0)
  	}        
    
    // Prevent warnings
    servletContext.setAttribute(
      "org.codehaus.groovy.grails.APPLICATION_CONTEXT", applicationContext)                         	
  	
  	ServletsGrailsPluginSupport.enhanceServletApi()
  	    	
  	mGrailsControllerAdapter = 
  	  new GrailsControllerAdapter(makeOptionsMap())  	
  }
  
  override def
  handleRequest
  (  
    request : HttpServletRequest,
    response : HttpServletResponse
  ) : ModelAndView =
  {
    require(request != null)
    require(response != null)
    
    val springModel = new ExtendedModelMap()
    addDefaultObjects(request, springModel)
    
    val rv = mGrailsControllerAdapter.handleRequest(request, response, 
        springModel, servletContext, applicationContext)
                            
    def computeDefaultViewName() : String =
    {      
      val requestToViewNameTranslator = 
        new DefaultRequestToViewNameTranslator()
          
      val viewName = requestToViewNameTranslator.getViewName(request)
      if (pathPrefix.isEmpty()) {        
      	viewName        
      } else {
        StringUtils.removeStart(viewName, pathPrefix.substring(1) + "/")
      }
    }
    
    rv match          
    {
      case null => null
      case s: String => new ModelAndView(s, springModel.asMap())
      case m: JMap[_, _] => {        
        springModel.addAllAttributes(m.asInstanceOf[JMap[String, Object]])
        new ModelAndView(computeDefaultViewName(), springModel.asMap())
      }
            
      case mav : ModelAndView => {
        // FIXME: overrides model properties
      	mav.getModel.putAll(springModel.asMap.asInstanceOf[JMap[String, Object]])
      	mav
      }       
      
      case x => throw new RuntimeException("Unknown return class: "  + 
          x.getClass)           
    }               
  }
  
  /** Add any objects to the model for all requests. */
  protected def 
  addDefaultObjects
  (
    request : HttpServletRequest,
    model : Model        
  ) : Unit = 
  {    
  }

  protected def makeOptionsMap() : JMap[String, Object] =
  {   
    val optionsMap = new JHashMap[String, Object]()
    optionsMap.put(OPTION_NAME_PATH_PREFIX, pathPrefix)
    optionsMap.put(OPTION_NAME_PATH_SUFFIX_TO_STRIP, pathSuffixToStrip)
    optionsMap.put(OPTION_NAME_URL_CASE_FORMAT, classOf[CaseFormat].
        getField(urlCaseFormat.toUpperCase(Locale.US)).get(null))
                
    optionsMap.put(OPTION_NAME_SOURCE_PATHS, sourcePaths)
    optionsMap.put(OPTION_NAME_URL_MAPPINGS_CLASS_NAME, urlMappingsClassName)
    optionsMap.put(OPTION_NAME_CONTROLLER_PACKAGE_NAME, controllerPackageName)
    optionsMap.put(OPTION_NAME_DEVELOPMENT_MODE, developmentMode)    
    optionsMap
  }
  
  protected var mGrailsControllerAdapter : GrailsControllerAdapter = _
  
  @BeanProperty
  protected var pathPrefix : String = DEFAULT_PATH_PREFIX

  @BeanProperty
  protected var pathSuffixToStrip : String = DEFAULT_PATH_SUFFIX_TO_STRIP
    
  @BeanProperty
  protected var urlCaseFormat : String = DEFAULT_URL_CASE_FORMAT
  
  @BeanProperty
  protected var sourcePaths : Array[String] = DEFAULT_SOURCE_PATHS 
    
  @BeanProperty
  protected var urlMappingsClassName : String = DEFAULT_URL_MAPPINGS_CLASS_NAME 
  
  @BeanProperty
  protected var controllerPackageName : String = DEFAULT_CONTROLLER_PACKAGE_NAME

  @BeanProperty
  protected var servletContext : ServletContext = _
  
  @BeanProperty
  protected var applicationContext : ApplicationContext = _
  
  @BeanProperty
  protected var developmentMode : JBoolean = DEFAULT_DEVELOPMENT_MODE
  
  private[this] val mLogger = LoggerFactory.getLogger(classOf[GrailsLikeController])
}

object GrailsLikeController 
{
  val OPTION_NAME_PATH_PREFIX = "pathPrefix"
  val OPTION_NAME_PATH_SUFFIX_TO_STRIP = "pathSuffixToStrip"
  val OPTION_NAME_URL_CASE_FORMAT = "urlCaseFormat"    
  val OPTION_NAME_SOURCE_PATHS = "sourcePaths"  
  val OPTION_NAME_URL_MAPPINGS_CLASS_NAME = "urlMappingsClassName"
  val OPTION_NAME_CONTROLLER_PACKAGE_NAME = "controllerPackageName"
  val OPTION_NAME_DEVELOPMENT_MODE = "developmentMode"
    
  val DEFAULT_PATH_PREFIX = "/grails"
  val DEFAULT_PATH_SUFFIX_TO_STRIP = ""
  val DEFAULT_URL_CASE_FORMAT = "LOWER_HYPHEN"
  val DEFAULT_SOURCE_PATHS = Array("src/main/groovy")  
  val DEFAULT_URL_MAPPINGS_CLASS_NAME = "com.ngweb.web.springmvc.grails.UrlMappings"
  val DEFAULT_CONTROLLER_PACKAGE_NAME = "com.ngweb.web.springmvc.grails"  
  val DEFAULT_DEVELOPMENT_MODE = JBoolean.FALSE
}
