package com.opc.rt;

import java.sql.*;
import java.util.*;

public class DBUtils {
    private final String url, user, pwd;

    public DBUtils(String url, String user, String pwd) {
        this.url = url;
        this.user = user;
        this.pwd = pwd;
    }

    public List<String> loadTags() throws Exception {
        List<String> list = new ArrayList<>();
        try (
            Connection conn = DriverManager.getConnection(url, user, pwd);
            PreparedStatement ps = conn.prepareStatement(
                "SELECT opc_label_name FROM instrument WHERE opc_label_name IS NOT NULL");
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }
}
