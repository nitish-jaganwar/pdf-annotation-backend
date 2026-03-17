package com.annotator.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/annotations")
public class AnnotationResource {

	// Database Connection Helper
	// private Connection getConnection() throws Exception {
	// 	Class.forName("com.mysql.cj.jdbc.Driver");
	// 	// Apne MySQL credentials yahan daalein
	// 	return DriverManager.getConnection("jdbc:mysql://localhost:3306/doc_annotation", "root", "root123");
	// }
    private Connection getConnection() throws Exception {

    Class.forName("com.mysql.cj.jdbc.Driver");

    String host = System.getenv("MYSQLHOST");
    String port = System.getenv("MYSQLPORT");
    String db   = System.getenv("MYSQLDATABASE");
    String user = System.getenv("MYSQLUSER");
    String pass = System.getenv("MYSQLPASSWORD");

    // fallback for local
    if (host == null) {
        host = "localhost";
        port = "3306";
        db   = "doc_annotation";
        user = "root";
        pass = "root123";
    }

    String url = "jdbc:mysql://" + host + ":" + port + "/" + db +
                 "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    return DriverManager.getConnection(url, user, pass);
}

	// 1. SAVE API (Upsert: Agar docId hai toh update karo, nahi toh insert karo)
	@POST
	@Path("/save/{docId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response saveAnnotations(@PathParam("docId") String docId, String jsonPayload) {
		try (Connection conn = getConnection()) {
			String sql = "INSERT INTO document_annotations (doc_id, json_data) VALUES (?, ?) "
					+ "ON DUPLICATE KEY UPDATE json_data = ?";

			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, docId);
			pstmt.setString(2, jsonPayload); // Poora state JSON
			pstmt.setString(3, jsonPayload);

			pstmt.executeUpdate();
			return Response.ok("{\"status\":\"success\", \"message\":\"Saved to DB\"}").build();

		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().entity("{\"error\":\"" + e.getMessage() + "\"}").build();
		}
	}

	// 2. LOAD API (Database se JSON fetch karna)
	@GET
	@Path("/load/{docId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response loadAnnotations(@PathParam("docId") String docId) {
		try (Connection conn = getConnection()) {
			String sql = "SELECT json_data FROM document_annotations WHERE doc_id = ?";
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, docId);

			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				String jsonData = rs.getString("json_data");
				return Response.ok(jsonData).build();
			} else {
				// Agar data nahi mila toh blank state bhej do
				return Response.ok("{\"annotations\":[], \"canvasData\":null}").build();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().entity("{\"error\":\"" + e.getMessage() + "\"}").build();
		}
	}

	@GET
	@Path("/file/{docId}")
	@Produces({ "application/pdf", "image/jpeg", "image/png" })
	public Response getOriginalDocument(@PathParam("docId") String docId) {

		// 1. Wo folder jahan aapne files save ki hain
		// (Production mein isko C drive ki jagah kisi server path ya AWS S3 par rakhte
		// hain)
		String folderPath = "C:/Users/NITISH JAGANWAR/Desktop/test/annot_files/";

		// 2. File ka poora rasta banayein (Abhi ke liye hum PDF assume kar rahe hain)
		File file = new File(folderPath + docId + ".pdf");

		// 3. Agar file nahi mili, toh 404 Not Found error return karein
		if (!file.exists()) {
			return Response.status(Response.Status.NOT_FOUND).entity("Error: Document not found for ID -> " + docId)
					.build();
		}

		// 4. File mil gayi toh browser ko seedha file bhej dein
		// "inline" ka matlab hai browser file ko download karne ki bajaye screen par
		// dikhayega
		return Response.ok(file).header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"").build();
	}

	@GET
    @Path("/export/pdfbox/{docId}")
    @Produces("application/pdf")
    public Response exportWithPdfBox(@PathParam("docId") String docId) {
        
        String safeDocId = docId.toUpperCase(); 
        File file = new File("C:\\Users\\NITISH JAGANWAR\\Desktop\\test\\annot_files\\" + safeDocId + ".pdf");
        
        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("Error: Keep " + safeDocId + ".pdf in C:/tbits_uploads/")
                           .type("text/plain")
                           .build();
        }

        String jsonPayload = null;
        String query = "SELECT json_data FROM document_annotations WHERE doc_id = ? LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, safeDocId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                jsonPayload = rs.getString("json_data");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity("Database error: " + e.getMessage()).build();
        }

        if (jsonPayload == null || jsonPayload.trim().isEmpty()) {
            return Response.ok(file)
                    .header("Content-Disposition", "inline; filename=\"" + safeDocId + "_Original.pdf\"")
                    .build();
        }

        try (PDDocument document = PDDocument.load(file)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonPayload);
            
            JsonNode annotationsArray = rootNode.path("annotations");
            JsonNode canvasObjects = rootNode.path("canvasData").path("objects");

            final float SCALE_FACTOR = 1.5f;

            for (JsonNode anno : annotationsArray) {
                if (anno.path("isDraft").asBoolean(true)) continue;

                String id = anno.path("id").asText();
                String type = anno.path("type").asText("Rectangle").toLowerCase(); 
                
                JsonNode shapeNode = null;
                if (canvasObjects.isArray()) {
                    for (JsonNode obj : canvasObjects) {
                        if (id.equals(obj.path("id").asText())) {
                            shapeNode = obj;
                            break;
                        }
                    }
                }

                if (shapeNode != null) {
                    
                    float scaleX = (float) shapeNode.path("scaleX").asDouble(1.0);
                    float scaleY = (float) shapeNode.path("scaleY").asDouble(1.0);
                    
                    float pdfX = (float) (shapeNode.path("left").asDouble() / SCALE_FACTOR);
                    float pdfY_fromTop = (float) (shapeNode.path("top").asDouble() / SCALE_FACTOR);
                    float pdfWidth = (float) ((shapeNode.path("width").asDouble() * scaleX) / SCALE_FACTOR);
                    float pdfHeight = (float) ((shapeNode.path("height").asDouble() * scaleY) / SCALE_FACTOR);

                    int pageIndex = anno.path("page").asInt(1) - 1;
                    if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) pageIndex = 0; 
                    PDPage page = document.getPage(pageIndex);
                    
                    float pageHeight = page.getMediaBox().getHeight();
                    float finalPdfY = pageHeight - pdfY_fromTop - pdfHeight;

                    //  Color Parsing
                    String hexColor = anno.path("color").asText("#3b6ef8").replace("#", "");
                    org.apache.pdfbox.pdmodel.graphics.color.PDColor pdColor = null;
                    if (hexColor.length() == 6) {
                        float r = Integer.parseInt(hexColor.substring(0, 2), 16) / 255f;
                        float g = Integer.parseInt(hexColor.substring(2, 4), 16) / 255f;
                        float b = Integer.parseInt(hexColor.substring(4, 6), 16) / 255f;
                        pdColor = new org.apache.pdfbox.pdmodel.graphics.color.PDColor(new float[]{r, g, b}, org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE);
                    }

                    //  Author & Text
                    String userName = anno.path("createdBy").path("name").asText("Reviewer");
                    String commentText = anno.path("text").asText("No comments");

                    // CHECK SHAPE TYPE
                    if (type.contains("arrow") || type.contains("line")) {
                        
                        // Arrow aur Line ke liye 'PDAnnotationLine' use hota hai
                        org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine lineAnnot = 
                            new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine();
                        
                        org.apache.pdfbox.pdmodel.common.PDRectangle pdRect = new org.apache.pdfbox.pdmodel.common.PDRectangle(pdfX, finalPdfY, pdfWidth, pdfHeight);
                        lineAnnot.setRectangle(pdRect);
                        
                        // Diagonal line banayein (Top-Left se Bottom-Right)
                        float startX = pdfX;
                        float startY = finalPdfY + pdfHeight; 
                        float endX = pdfX + pdfWidth;
                        float endY = finalPdfY; 
                        
                        lineAnnot.setLine(new float[] { startX, startY, endX, endY });
                        
                        // Agar Arrow hai, toh line ke aage teer (arrowhead) laga dein
                        if (type.contains("arrow")) {
                            lineAnnot.setEndPointEndingStyle(org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine.LE_CLOSED_ARROW);
                        }

                        lineAnnot.setContents(commentText);
                        lineAnnot.setTitlePopup(userName); 
                        lineAnnot.setAnnotationName(userName);
                        if (pdColor != null) lineAnnot.setColor(pdColor);
                        
                        page.getAnnotations().add(lineAnnot);
                        
                    } else {
                        // Rectangle aur Ellipse/Circle ka logic wahi rahega
                        String subType = org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle.SUB_TYPE_SQUARE;
                        if (type.contains("ellipse") || type.contains("circle")) {
                            subType = org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle.SUB_TYPE_CIRCLE;
                        }

                        org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle annotation = 
                            new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle(subType);
                        
                        org.apache.pdfbox.pdmodel.common.PDRectangle pdRect = new org.apache.pdfbox.pdmodel.common.PDRectangle(pdfX, finalPdfY, pdfWidth, pdfHeight);
                        annotation.setRectangle(pdRect);
                        
                        annotation.setContents(commentText);
                        annotation.setAnnotationName(userName);
                        annotation.setTitlePopup(userName);
                        if (pdColor != null) annotation.setColor(pdColor);

                        page.getAnnotations().add(annotation);
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);

            return Response.ok(baos.toByteArray())
                    .type("application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + safeDocId + "_Annotated.pdf\"") 
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity("PDF Generation Failed: " + e.getMessage()).build();
        }
    }
}