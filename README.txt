==========================================================================
=== Grails Goodies for Spring MVC  
==========================================================================

@author Jeff Tsay

This Grails Goodies for Spring MVC adds some features in Grails that are not 
available in Spring MVC Java web applications. Namely:

1) URL routing. See http://grails.org/doc/latest/guide/theWebLayer.html#urlmappings

As a consequence of URL routing being done in Groovy, all Groovy code will be
reloaded dynamically, so there is no need to restart the server between code 
changes! This only happens in development mode. When in production mode, all
Groovy code is compiled statically so there is no slowdown due to reloading. 

2) BeanBuilder defined beans mixed with XML-defined beans. The class
com.ngweb.web.springmvc.grails.HybridWebApplicationContext can take either
XML or Groovy files (which contain bean definitions written with BeanBuilder DSL).
Bean references can be to either source of definitions. 

This library is intended for projects that have a significant investment in 
Spring MVC already, but want to get some of the advantages of Grails such as 
dynamic code reloading, without undergoing a long conversion process to Grails.

See https://github.com/jtsay362/spring-mvc-grails-integration for an example of 
how to use this library.