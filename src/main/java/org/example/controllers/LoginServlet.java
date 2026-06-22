package org.example.controllers;
import org.example.config.MongoConfig;
import org.example.models.User;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.bson.Document;
import java.io.IOException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = request.getParameter("login");
        String password = request.getParameter("password");

        MongoDatabase db = MongoConfig.getDatabase();
        MongoCollection<Document> usersCollection = db.getCollection("users");

        Document userDoc = usersCollection.find(eq("email", email)).first();

        if (userDoc != null) {
            String dbPassword = userDoc.getString("password_hash");

            if (dbPassword.equals(password)) {
                User user = new User(
                        userDoc.getObjectId("_id"),
                        userDoc.getString("username"),
                        userDoc.getString("email"),
                        dbPassword,
                        true
                );

                usersCollection.updateOne(eq("_id", user.getId()), new Document("$set", new Document("is_online", true)));

                HttpSession session = request.getSession();
                session.setAttribute("currentUser", user);

                response.sendRedirect("messenger.jsp");
                return;
            }
        }

        request.setAttribute("error", "Невірний емейл або пароль!");
        request.getRequestDispatcher("login_page.jsp").forward(request, response);
    }
}
