package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class SvnRepositoryGetInfo extends SvnOperation<SVNLogEntry> {
    
    private File repositoryRoot;
    private String transactionName;
    
    public SvnRepositoryGetInfo(SvnOperationFactory factory) {
        super(factory);
    }

	public File getRepositoryRoot() {
		return repositoryRoot;
	}

	public void setRepositoryRoot(File repositoryRoot) {
		this.repositoryRoot = repositoryRoot;
	}
	
	public String getTransactionName() {
		return transactionName;
	}

	public void setTransactionName(String transactionName) {
		this.transactionName = transactionName;
	}

	

	    
}