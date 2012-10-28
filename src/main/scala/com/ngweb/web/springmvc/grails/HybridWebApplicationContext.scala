package com.ngweb.web.springmvc.grails

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.web.context.support.ServletContextResource
import org.springframework.web.context.support.XmlWebApplicationContext

import grails.spring.BeanBuilder

/** Loads both xml and groovy (using BeanBuilder) bean definitions. */
class HybridWebApplicationContext extends XmlWebApplicationContext 
{  
  override protected def 
  loadBeanDefinitions
  (
    beanFactory : DefaultListableBeanFactory
  ) : Unit =
  {
    super.loadBeanDefinitions(beanFactory)
    
    loadGroovyBeanDefinitions(beanFactory)
  }
  
  override protected def 
  loadBeanDefinitions
  (
    reader : XmlBeanDefinitionReader 
  ) : Unit =
  {
    val configLocations = getConfigLocations()
    
    if (configLocations != null) 
    {
      configLocations.filter(_.toLowerCase().endsWith(".xml")).foreach(
        location => reader.loadBeanDefinitions(location)        
      )   
    }  
  }
  
  private[this] def 
  loadGroovyBeanDefinitions
  (
    beanFactory : DefaultListableBeanFactory    
  ) : Unit =
  {
    val configLocations = getConfigLocations()
    
    if (configLocations != null)
    {      
	  	configLocations.filter(_.toLowerCase().endsWith(".groovy")).foreach(
	  	  location =>	
	 			{
	 				val beanBuilder = new BeanBuilder(getParent(), getClassLoader())		
	     
	 				val resource = new ServletContextResource(getServletContext(), location)
	     
	 				beanBuilder.loadBeans(resource)
	
	 				beanBuilder.registerBeans(beanFactory)
	 			}
	    )
    }
  }  
}