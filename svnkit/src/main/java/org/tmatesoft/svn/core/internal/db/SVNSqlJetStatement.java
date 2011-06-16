/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.db;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNSqlJetStatement {

    protected SVNSqlJetDb sDb;
    private ISqlJetCursor cursor;
    protected List binds = new ArrayList();
    protected SqlJetTransactionMode transactionMode = SqlJetTransactionMode.READ_ONLY;

    protected ISqlJetCursor openCursor() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public long insert(Object... data) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public long exec() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public SVNSqlJetStatement(SVNSqlJetDb sDb) {
        this.sDb = sDb;
        setCursor(null);
    }

    public List getBinds() {
        return binds;
    }

    public boolean isNeedsReset() {
        return getCursor() != null;
    }

    public void reset() throws SVNException {
        binds.clear();
        if (isNeedsReset()) {
            try {
                getCursor().close();
            } catch (SqlJetException e) {
                SVNSqlJetDb.createSqlJetError(e);
            } finally {
                setCursor(null);
                sDb.commit();
            }
        }
    }

    public boolean next() throws SVNException {
        try {
            if (getCursor() == null) {
                sDb.beginTransaction(transactionMode);
                setCursor(openCursor());
                return !getCursor().eof();
            }
            return getCursor().next();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public boolean eof() throws SVNException {
        try {
            if (getCursor() == null) {
                sDb.beginTransaction(transactionMode);
                setCursor(openCursor());
            }
            return getCursor().eof();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public void bindf(String format, Object... data) throws SVNException {

        int n = 0;
        int length = data.length;

        for (int i = 0; i < format.length(); i++) {

            char fmt = format.charAt(i);

            switch (fmt) {
                case 's':
                case 't':
                    if (n > length || data[n] == null) {
                        bindNull(i + 1);
                    } else if (data[n] instanceof File) {
                        bindString(i + 1, SVNFileUtil.getFilePath((File) data[n]));
                    } else {
                        bindString(i + 1, data[n].toString());
                    }
                    n++;
                    break;

                case 'i':
                    if (n > length || data[n] == null) {
                        bindNull(i + 1);
                    } else if (data[n] instanceof Number) {
                        bindLong(i + 1, ((Number) data[n]).longValue());
                    } else if (data[n] instanceof SVNDate) {
                        bindLong(i + 1, ((SVNDate) data[n]).getTimeInMicros());
                    } else {
                        SVNErrorManager.assertionFailure(false, String.format("Number argument required in %d", i + 1), SVNLogType.WC);
                    }
                    n++;
                    break;

                case 'r':
                    if (n > length || data[n] == null) {
                        bindNull(i + 1);
                    } else if (data[n] instanceof Number) {
                        bindRevision(i + 1, ((Number) data[n]).longValue());
                    } else {
                        SVNErrorManager.assertionFailure(false, String.format("Number argument required in %d", i + 1), SVNLogType.WC);
                    }
                    n++;
                    break;

                case 'b':
                    if (n > length || data[n] == null) {
                        bindNull(i + 1);
                    } else if (data[n] instanceof byte[]) {
                        bindBlob(i + 1, (byte[]) data[n]);
                    } else {
                        SVNErrorManager.assertionFailure(false, String.format("Byte array argument required in %d", i + 1), SVNLogType.WC);
                    }
                    n++;
                    break;

                case 'n':
                    bindNull(i + 1);
                    break;

                default:
                    SVNErrorManager.assertionFailure(false, String.format("Unknown format '%s' in %d", fmt, i + 1), SVNLogType.WC);
            }
        }

    }

    private void adjustBinds(int i) {
        int size = binds.size();
        if (size < i) {
            for (int n = size; n < i; n++) {
                binds.add(null);
            }
        }
    }

    public void bindNull(int i) {
        adjustBinds(i);
        binds.set(i - 1, null);
    }

    public void bindLong(int i, long v) {
        adjustBinds(i);
        binds.set(i - 1, v);
    }

    public void bindString(int i, String string) {
        adjustBinds(i);
        binds.set(i - 1, string);
    }

    public void bindProperties(int i, SVNProperties props) throws SVNException {
        adjustBinds(i);
        binds.set(i - 1, props != null ? SVNSkel.createPropList(props.asMap()).unparse() : null);
    }

    public void bindChecksum(int i, SVNChecksum checksum) {
        adjustBinds(i);
        binds.set(i - 1, checksum != null ? checksum.toString() : null);
    }

    public void bindBlob(int i, byte[] serialized) {
        adjustBinds(i);
        binds.set(i - 1, serialized);
    }

    public void bindRevision(int i, long revision) {
        adjustBinds(i);
        if (SVNRevision.isValidRevisionNumber(revision)) {
            bindLong(i, revision);
        } else {
            bindNull(i);
        }
    }

    protected Object getBind(int i) {
        adjustBinds(i);
        return binds.get(i - 1);
    }

    public long count() throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return 0;
            return getCursor().getRowCount();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return 0;
        }
    }

    public Object getColumn(Enum f) throws SVNException {
        return getColumn(f.toString());
    }

    public Object getColumn(String f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return null;
            return getCursor().getValue(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public long getColumnLong(Enum f) throws SVNException {
        return getColumnLong(f.toString());
    }

    public long getColumnLong(String f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return 0;
            return getCursor().getInteger(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return 0;
        }
    }

    public String getColumnString(Enum f) throws SVNException {
        return getColumnString(f.toString());
    }

    public String getColumnString(String f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return null;
            return getCursor().getString(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public boolean isColumnNull(Enum f) throws SVNException {
        return isColumnNull(f.toString());
    }

    public boolean isColumnNull(String f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return true;
            return getCursor().isNull(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public byte[] getColumnBlob(Enum f) throws SVNException {
        return getColumnBlob(f.toString());
    }

    public byte[] getColumnBlob(String f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return null;
            return getCursor().getBlobAsArray(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public boolean getColumnBoolean(int f) throws SVNException {
        return getColumnLong(f) != 0;
    }

    public boolean getColumnBoolean(Enum f) throws SVNException {
        return getColumnLong(f) != 0;
    }

    public long getColumnLong(int f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return 0;
            return getCursor().getInteger(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return 0;
        }
    }

    public String getColumnString(int f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return null;
            return getCursor().getString(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public boolean isColumnNull(int f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return true;
            return getCursor().isNull(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return true;
        }
    }

    public byte[] getColumnBlob(int f) throws SVNException {
        try {
            if (getCursor() == null || getCursor().eof())
                return null;
            return getCursor().getBlobAsArray(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public SVNSqlJetStatement getJoinedStatement(String joinedTable) throws SVNException {
        SVNErrorManager.assertionFailure(false, "unsupported", SVNLogType.WC);
        return null;
    }

    public SVNSqlJetStatement getJoinedStatement(Enum joinedTable) throws SVNException {
        return getJoinedStatement(joinedTable.toString());
    }

    public SVNProperties getColumnProperties(Enum f) throws SVNException {
        return getColumnProperties(f.name());
    }

    public SVNProperties getColumnProperties(String f) throws SVNException {
        if (isColumnNull(f))
            return null;
        final byte[] val = getColumnBlob(f);
	    return parseProperties(val);
    }

    public static SVNProperties parseProperties(byte[] val) throws SVNException {
        if (val == null)
            return null;
        final SVNSkel skel = SVNSkel.parse(val);
        if (!skel.isValidPropList()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_MALFORMED_SKEL, "proplist");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        return SVNProperties.wrap(skel.parsePropList());
    }

    public long done() throws SVNException {
        try {
            return exec();
        } finally {
            reset();
        }
    }

    public void nextRow() throws SVNException {
        if (!next()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, "Expected database row missing");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
    }

    public long getColumnRevnum(Enum f) throws SVNException {
        if (isColumnNull(f)) {
            return -1;
        }
        return getColumnLong(f);
    }

    protected ISqlJetCursor getCursor() {
        return cursor;
    }

    protected void setCursor(ISqlJetCursor cursor) {
        this.cursor = cursor;
    }

    public Map<String, Object> getRowValues() throws SVNException {
        throw new UnsupportedOperationException();
    }

}