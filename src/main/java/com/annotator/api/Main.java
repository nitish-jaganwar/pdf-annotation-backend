package com.annotator.api;

import java.io.File;

import javax.websocket.server.ServerContainer;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import com.annotator.ws.AnnotationWebSocket;

public class Main {

	public static void main(String[] args) throws Exception {

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(8080);

		// ✅ Must call getConnector() in Tomcat 9
		tomcat.getConnector();

		// ✅ Context with empty string = root
		// Context context = tomcat.addContext("", null);

		Context context = tomcat.addContext("", new File(".").getAbsolutePath());
		context.addApplicationListener("org.apache.tomcat.websocket.server.WsContextListener");
		// Jersey ResourceConfig
		ResourceConfig config = new ResourceConfig();
		config.packages("com.annotator.api"); // your package

		// Add Jersey servlet
		ServletContainer jerseyServlet = new ServletContainer(config);
		Tomcat.addServlet(context, "jersey-servlet", jerseyServlet);

		// Map /api/* to jersey
		context.addServletMappingDecoded("/api/*", "jersey-servlet");

		Tomcat.addServlet(context, "default", new org.apache.catalina.servlets.DefaultServlet());
		context.addServletMappingDecoded("/", "default");

		tomcat.start();

		// get websocket container
		ServerContainer wscontainer = (ServerContainer) context.getServletContext()
				.getAttribute("javax.websocket.server.ServerContainer");

		if (wscontainer == null) {
			throw new RuntimeException("❌ WebSocket container not initialized");
		}

		// register endpoint
		wscontainer.addEndpoint(AnnotationWebSocket.class);
		
		config.register(com.annotator.config.CorsFilter.class);
		// wscontainer.addEndpoint(com.annotator.ws.AnnotationWebSocket.class);

		System.out.println(" WebSocket endpoint registered: /ws-annotator/{docId}");
		System.out.println(" Server started!");
		System.out.println(" http://localhost:8080/api/hello");

		tomcat.getServer().await();
	}
}