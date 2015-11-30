/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testapplication1;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author siwaweswongcharoen
 */
public class TestApplication1 {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        System.out.println("Hello World");
        
        // /Users/siwaweswongcharoen/Downloads/RICEHOUSE.GDB
        String url = "jdbc:interbase://localhost/Users/siwaweswongcharoen/Downloads/RICEHOUSE.GDB";
//        String url = "jdbc:firebirdsql:localhost/Users/siwaweswongcharoen/Downloads/RICEHOUSE.GDB";
        try {
//            Class<?> forName;
//            forName = Class.forName("interbase.interclient.Driver");
            Class.forName("interbase.interclient.Driver");
//            Class.forName("org.firebirdsql.jdbc.FBDriver");
//            Driver d = new InterBase.interclient.Driver();

            Connection conn;
            conn = DriverManager.getConnection(url, "sysdba", "masterkey");
            
            Statement stmt = conn.createStatement();
        } catch (SQLException | ClassNotFoundException ex) {
            Logger.getLogger(TestApplication1.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
    }
    
}
