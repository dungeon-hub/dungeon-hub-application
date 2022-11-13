/*
 * KissenEssentials
 * Copyright (C) KissenEssentials team and contributors.
 *
 * This program is free software and is free to redistribute
 * and/or modify under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is intended for the purpose of joy,
 * WITHOUT WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.taubsie.carrylogs;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.sql.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class DatabaseService
{
    private static DatabaseService instance;
    private Connection connection;

    public static DatabaseService getInstance()
    {
        if (instance == null)
        {
            instance = new DatabaseService();
        }

        return instance;
    }

    private DatabaseService()
    {
        try
        {
            if (System.getProperty("os.name").contains("Windows"))
            {
                this.connection = DriverManager.getConnection("jdbc:mysql://h2987929.stratoserver.net:3306/carrylogs?useUnicode=yes&characterEncoding=UTF-8&characterSetResults=UTF-8&autoReconnect=true", "taubussy", "h2987929");
            }
            else
            {
                this.connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/carrylogs?useUnicode=yes&characterEncoding=UTF-8&characterSetResults=UTF-8&autoReconnect=true", "taubussy", "h2987929");
            }
        }
        catch (SQLException e)
        {
            System.err.println("Error while trying to connect to database.");
        }
    }

    public CachedRowSet select(String selection, String table, String where) throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement("select " + selection + " from " + table + " where " + where);
        ResultSet resultSet = preparedStatement.executeQuery();

        try (CachedRowSet cachedRowSet = RowSetProvider.newFactory().createCachedRowSet())
        {
            cachedRowSet.populate(resultSet);
            return cachedRowSet.createCopyNoConstraints();
        }
    }

    public CachedRowSet selectAll(String selection, String table) throws SQLException
    {
        return select(selection, table, "1 = '1'");
    }

    public void insert()
    {

    }

    public void update()
    {

    }

    public void delete()
    {

    }
}