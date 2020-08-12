package transaction;

import java.rmi.*;

/** 
 * Interface for the Transaction Manager of the Distributed Travel
 * Reservation System.
 * <p>
 * Unlike WorkflowController.java, you are supposed to make changes
 * to this file.
 */

public interface TransactionManager extends Remote {

    public boolean dieNow()
	throws RemoteException;

    public void ping() throws RemoteException;
    
	public void enlist(int xid, ResourceManager rm) throws RemoteException, InvalidTransactionException;

	
    /** The RMI name a TransactionManager binds to. */
    public static final String RMIName = "TM";

    public int start() throws RemoteException;

    public boolean commit(int xid) throws RemoteException, InvalidTransactionException, TransactionAbortedException;

    public void abort(int xid) throws RemoteException, InvalidTransactionException;

    public void setDieTime(String time) throws RemoteException;

    public final static String TM_TRANSACTION_NUM_LOG_FILENAME = "data/tm_xidNum.log";
    public final static String TM_TRANSACTION_LOG_FILENAME = "data/tm_xids.log";
    public final static String TM_TRANSACTION_RMs_LOG_FILENAME = "data/tm_xidRMs.log";

}
