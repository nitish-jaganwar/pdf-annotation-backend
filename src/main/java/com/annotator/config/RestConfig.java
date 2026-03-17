package com.annotator.config;


import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

// Yeh Tomcat ke context.addServletMappingDecoded("/api/*", ...) ka replacement hai
@ApplicationPath("/api")
public class RestConfig extends Application {
	// GlassFish apne aap saare @Path aur @Provider (jaise aapka CorsFilter) dhoondh
	// lega.
	// Iske andar kuch aur likhne ki zaroorat nahi hai!
}