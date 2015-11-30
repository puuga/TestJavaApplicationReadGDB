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

        TestApplication1 t = new TestApplication1();
        t.jdbc();
//        t.dataSource();
    }

    void jdbc() {
        // /Users/siwaweswongcharoen/Downloads/RICEHOUSE.GDB
        String url = "jdbc:interbase://localhost/Users/siwaweswongcharoen/Downloads/RICEHOUSE.GDB";
//        String url = "jdbc:firebirdsql:local:Users/siwaweswongcharoen/Downloads/RICEHOUSE.GDB";
        try {
//            Class<?> forName;
//            forName = Class.forName("interbase.interclient.Driver");
            Class.forName("interbase.interclient.Driver");
//            Class.forName("org.firebirdsql.jdbc.FBDriver");
//            Driver d = new InterBase.interclient.Driver();

            Connection conn;
            conn = DriverManager.getConnection(url, "SYSDBA", "masterkey");

//            Statement stmt = conn.createStatement();
        } catch (SQLException | ClassNotFoundException ex) {
            Logger.getLogger(TestApplication1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void dataSource() {
        org.firebirdsql.pool.FBWrappingDataSource dataSource
                = new org.firebirdsql.pool.FBWrappingDataSource();

        // Set the standard properties
        dataSource.setDatabase("localhost/3050:Users/siwaweswongcharoen/Downloads/RICEHOUSE.GDB");
        dataSource.setDescription("An example database of employees");

        dataSource.setType("TYPE4");

        try {
            dataSource.setLoginTimeout(10);
            java.sql.Connection c = dataSource.getConnection("sysdba", "masterkey");

            java.sql.Statement stmt = c.createStatement();
//          java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM test_charset");
//          while(rs.next())
//              System.out.println("a1 = " + rs.getString(1) + ", a2 = " + rs.getString(2));

            stmt.close();

            // At this point, there is no implicit driver instance
            // registered with the driver manager!
            System.out.println("got connection");
            c.close();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            System.out.println("sql exception: " + e.getMessage());
        }
    }

}
