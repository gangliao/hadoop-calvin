package org.apache.hadoop.hdfs.server.namenode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

public class DatabaseDatablock {
  public static void insertBlock(final long blkid, final long len, final long genStamp) {
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();

      String sql = "INSERT INTO datablocks(blockId, numBytes, generationStamp) VALUES (?, ?, ?);";

      PreparedStatement pst = conn.prepareStatement(sql);

      pst.setLong(1, blkid);
      pst.setLong(2, len);
      pst.setLong(3, genStamp);

      pst.executeUpdate();
      pst.close();
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }
  }

  private static <T> void setAttribute(final long id, final String attrName, final T attrValue) {
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();

      String sql = "UPDATE datablocks SET " + attrName + " = ? WHERE blockId = ?;";
      PreparedStatement pst = conn.prepareStatement(sql);

      if (attrValue instanceof String) {
        if (attrValue.toString() == null) {
          pst.setNull(1, java.sql.Types.VARCHAR);
        } else {
          pst.setString(1, attrValue.toString());
        }
      } else if (attrValue instanceof Integer || attrValue instanceof Long) {
        pst.setLong(1, ((Long) attrValue).longValue());
      } else {
        System.err.println("Only support string and long types for now.");
        System.exit(0);
      }
      pst.setLong(2, id);

      pst.executeUpdate();
      pst.close();
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }
    LOG.info(attrName + " [UPDATE]: (" + id + "," + attrValue + ")");
  }

  private static <T> T getAttribute(final long id, final String attrName) {
    T result = null;
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();
      String sql = "SELECT " + attrName + " FROM datablocks WHERE blockId = ?;";
      PreparedStatement pst = conn.prepareStatement(sql);
      pst.setLong(1, id);
      ResultSet rs = pst.executeQuery();
      while (rs.next()) {
        ResultSetMetaData rsmd = rs.getMetaData();
        if (rsmd.getColumnType(1) == Types.BIGINT || rsmd.getColumnType(1) == Types.INTEGER) {
          result = (T) Long.valueOf(rs.getLong(1));
        } else if (rsmd.getColumnType(1) == Types.VARCHAR) {
          result = (T) rs.getString(1);
        }
      }
      rs.close();
      pst.close();
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }

    LOG.info(attrName + " [GET]: (" + id + "," + result + ")");

    return result;
  }

  public static long getNumBytes(final long blockId) {
    return getAttribute(blockId, "numBytes");
  }

  public static long getGenerationStamp(final long blockId) {
    return getAttribute(blockId, "generationStamp");
  }

  public static short getReplication(final long blockId) {
    return getAttribute(blockId, "replication");
  }

  public static void setBlockId(final long blockId, final long bid) {
    setAttribute(blockId, "blockId", bid);
  }

  public static void setNumBytes(final long blockId, final long numBytes) {
    setAttribute(blockId, "numBytes", numBytes);
  }

  public static void setGenerationStamp(final long blockId, final long generationStamp) {
    setAttribute(blockId, "generationStamp", generationStamp);
  }

  public static void setReplication(final long blockId, final short replication) {
    setAttribute(blockId, "replication", replication);
  }

  // BlockTuple is used to store BlockInfo from Database
  class BlockTuple {
    public long blockId;
    public long numBytes;
    public long generationStamp;
    public int replication;

    public void BlockTuple(long blockId, long numBytes, long generationStamp, int replication) {
      this.blockId = blockId;
      this.numBytes = numBytes;
      this.generationStamp = generationStamp;
      this.replication = replication;
    }
  }

  public static BlockTuple getBlock(final long blockId) {
    long blockId;
    long numBytes;
    long generationStamp;
    int replication;
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();
      String sql = "SELECT * FROM datablocks WHERE blockId = ?;";
      PreparedStatement pst = conn.prepareStatement(sql);
      pst.setLong(1, id);
      ResultSet rs = pst.executeQuery();
      while (rs.next()) {
        blockId = rs.getLong(1);
        numBytes = rs.getLong(2);
        generationStamp = rs.getLong(3);
        replication = rs.getInt(4);
      }
      rs.close();
      pst.close();
    } catch (SQLException ex) {
      System.out.println(ex.getMessage());
    }

    return new BlockTuple(blockId, numBytes, generationStamp, replication);
  }

  public static void delete(final long blockId) {
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();
      String sql = "DELETE FROM datablocks WHERE blockId = ?;";
      PreparedStatement pst = conn.prepareStatement(sql);
      pst.setLong(1, blockId);
      pst.executeUpdate();
      pst.close();
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }
  }

  public static void delete(final long nodeId, final int index) {
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();
      String sql =
          "DELETE FROM datablocks WHERE blockId = ("
              + "  SELECT blockId FROM inode2block WHERE id = ? and index = ?"
              + ");";
      PreparedStatement pst = conn.prepareStatement(sql);
      pst.setLong(1, nodeId);
      pst.setInt(2, index);
      pst.executeUpdate();
      pst.close();
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }
  }

  public static void removeBlock(final long blockId) {
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();
      String sql =
          "BEGIN;"
              + "DELETE FROM inode2block WHERE blockId = ?;"
              + "DELETE FROM datablocks WHERE blockId = ?;"
              + "COMMIT;";
      PreparedStatement pst = conn.prepareStatement(sql);
      pst.setLong(1, blockId);
      pst.setLong(2, blockId);
      pst.executeUpdate();
      pst.close();
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }
  }

  public static void removeAllBlocks(final long inodeId) {
    try {
      Connection conn = DatabaseConnection.getInstance().getConnection();
      String sql =
          "BEGIN;"
              + "DELETE FROM datablocks WHERE blockId = ("
              + "   SELECT blockId from inode2block where id = ?"
              + ");"
              + "DELETE FROM inode2block where id = ?;"
              + "COMMIT;";
      PreparedStatement pst = conn.prepareStatement(sql);
      pst.setLong(1, inodeId);
      pst.setLong(2, inodeId);
      pst.executeUpdate();
      pst.close();
    } catch (SQLException ex) {
      System.err.println(ex.getMessage());
    }
  }
}