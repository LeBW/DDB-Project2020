package transaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.rmi.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Transaction Manager for the Distributed Travel Reservation System.
 * 
 * Description: toy implementation of the TM
 */

public class TransactionManagerImpl extends java.rmi.server.UnicastRemoteObject implements TransactionManager {

	public static void main(String args[]) {
		System.setSecurityManager(new RMISecurityManager());

		String rmiPort = System.getProperty("rmiPort");
		if (rmiPort == null) {
			rmiPort = "";
		} else if (!rmiPort.equals("")) {
			rmiPort = "//:" + rmiPort + "/";
		}

		try {
			TransactionManagerImpl obj = new TransactionManagerImpl();
			Naming.rebind(rmiPort + TransactionManager.RMIName, obj);
			System.out.println("TM bound");
		} catch (Exception e) {
			System.err.println("TM not bound:" + e);
			System.exit(1);
		}
	}
	// xid -> relevant ResourceManagers
	private Map<Integer, Set<ResourceManager>> enlistRMs;
	// automatically increased xid count.
	private Integer xidCount;
	// xid -> trasactional status
	private Map<Integer, String> xidStatusMap;

	private String dieTime;

	public void ping() throws RemoteException {
	}

	// ResourceManager 调用这个函数，用来通知TransctionManager哪个ResourceManager参与了哪个事务
	public void enlist(int xid, ResourceManager rm) throws RemoteException, InvalidTransactionException {
		if (!this.xidStatusMap.containsKey(xid)) {
			rm.abort(xid);
			return;
		}

		synchronized(this.xidStatusMap) {
			String status = this.xidStatusMap.get(xid);
			if (status.equals("ABORTED")) {
				rm.abort(xid);

				synchronized(this.enlistRMs) {
					Set<ResourceManager> rms = enlistRMs.get(xid);
					ResourceManager tmp = rms.iterator().next();
					rms.remove(tmp);
					if (rms.size() > 0) {
						enlistRMs.put(xid, rms);
						this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, enlistRMs);
					}
					else {
						enlistRMs.remove(xid);
						this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, enlistRMs);

						this.xidStatusMap.remove(xid);
						this.storeToFile(TM_TRANSACTION_LOG_FILENAME, xidStatusMap);
					}
				}
				return;
			}
			else if (status.equals("COMMITED")) {
				rm.commit(xid);

				synchronized(this.enlistRMs) {
					Set<ResourceManager> rms = enlistRMs.get(xid);
					ResourceManager tmp = rms.iterator().next();
					rms.remove(tmp);
					if (rms.size() > 0) {
						enlistRMs.put(xid, rms);
						this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, enlistRMs);
					}
					else {
						enlistRMs.remove(xid);
						this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, enlistRMs);

						this.xidStatusMap.remove(xid);
						this.storeToFile(TM_TRANSACTION_LOG_FILENAME, xidStatusMap);
					}
				}
				return;
			}

			synchronized(this.enlistRMs) {
				Set<ResourceManager> temp = this.enlistRMs.get(xid);
                ResourceManager findSameRMId = null;
                boolean abort = false;
                for (ResourceManager r : temp) {
                    try {
                        if (r.getID().equals(rm.getID())) {
                            findSameRMId = r;
                        }
                    } catch (Exception e) {
                        // if some RM die, then r.getID() will cause an exception
                        // dieRM, dieRMAfterEnlist,
                        abort = true;
                        break;
                    }
                }

                if (abort) {
                    rm.abort(xid);

                    ResourceManager randomRemove = temp.iterator().next();
                    temp.remove(randomRemove);
                    if (temp.size() > 0) {
                        this.enlistRMs.put(xid, temp);
                        this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, this.enlistRMs);

                        // for dieRM, dieRMAfterEnlist
                        this.xidStatusMap.put(xid, "ABORTED");
                        this.storeToFile(TM_TRANSACTION_LOG_FILENAME, this.enlistRMs);
                    } else {
                        this.enlistRMs.remove(xid);
                        this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, this.enlistRMs);

                        this.xidStatusMap.remove(xid);
                        this.storeToFile(TM_TRANSACTION_LOG_FILENAME, this.xidStatusMap);
                    }

					return;
				}

				// 新的 enlist
				if (findSameRMId == null) {
                    temp.add(rm);
                    this.enlistRMs.put(xid, temp);
                    this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, this.enlistRMs);
                    return;
                }
			}
		}
	}

	public TransactionManagerImpl() throws RemoteException {
		// init
		enlistRMs = new HashMap<>();
		xidStatusMap = new HashMap<>();
		dieTime = "NoDie";
		xidCount = 1;


	}

	public boolean dieNow() throws RemoteException {
		System.exit(1);
		return true; // We won't ever get here since we exited above;
						// but we still need it to please the compiler.
	}

	@Override
	public int start() throws RemoteException {
		synchronized (this.xidCount) {
            Integer curXid = this.xidCount++;
            this.storeToFile(TM_TRANSACTION_NUM_LOG_FILENAME, this.xidCount);

            synchronized (this.xidStatusMap) {
                this.xidStatusMap.put(curXid, "NEW");
                this.storeToFile(TM_TRANSACTION_LOG_FILENAME, this.xidStatusMap);
            }

            synchronized (this.enlistRMs) {
                this.enlistRMs.put(curXid, new HashSet<>());
                this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, this.enlistRMs);
            }

            return curXid;
        }
		
	}

	@Override
	public boolean commit(int xid) throws RemoteException, InvalidTransactionException, TransactionAbortedException {
		if (!this.xidStatusMap.containsKey(xid)) {
            throw new TransactionAbortedException(xid, "TM commit");
        }

        // prepare
        Set<ResourceManager> resourceManagers = this.enlistRMs.get(xid);
        for (ResourceManager resourceManager : resourceManagers) {
            try {
                boolean prepared = resourceManager.prepare(xid);
                if (!prepared) {
                    this.abort(xid);
                    throw new TransactionAbortedException(xid, "commit prepare");
                }
            } catch (Exception e) {
                // occur when RM die, AfterPrepare, BeforePrepare
                // e.printStackTrace();
                this.abort(xid);
                throw new TransactionAbortedException(xid, "commit prepare");
            }
        }

        // write the prepare log to disk and mark xid PREPARED
        synchronized (this.xidStatusMap) {
            xidStatusMap.put(xid, "PREPARED");
            this.storeToFile(TM_TRANSACTION_LOG_FILENAME, this.xidStatusMap);
        }

        // the TM fails after it has received
        // "prepared" messages from all RMs, but before it can log "committed"
        // if this occur, all rm call enlist later when TM restart,
        // and enlist will mark xid ABORTED
        if (this.dieTime.equals("BeforeCommit")) {
            this.dieNow();
        }

        // write the prepare log to disk and mark xid COMMITTED
        synchronized (this.xidStatusMap) {
            xidStatusMap.put(xid, "COMMITED");
            this.storeToFile(TM_TRANSACTION_LOG_FILENAME, this.xidStatusMap);
        }

        // the TM fails right after it logs "committed"
        // if this occur, recoverFromFile will recover the xid
        // enlist commit RM
        if (this.dieTime.equals("AfterCommit")) {
            this.dieNow();
        }

        Set<ResourceManager> committedRMs = new HashSet<>();
        for (ResourceManager resourceManager : resourceManagers) {
            try {
                resourceManager.commit(xid);
                committedRMs.add(resourceManager);
            } catch (Exception e) {
//                e.printStackTrace();
                // FdieRMBeforeCommit
            }
        }

        if (committedRMs.size() == resourceManagers.size()) {
            synchronized (this.enlistRMs) {
                this.enlistRMs.remove(xid);
                this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, this.enlistRMs);
            }

            synchronized (this.xidStatusMap) {
                this.xidStatusMap.remove(xid);
                this.storeToFile(TM_TRANSACTION_LOG_FILENAME, this.xidStatusMap);
            }
        } else {
            // FdieRMBeforeCommit
            synchronized (this.enlistRMs) {
                resourceManagers.removeAll(committedRMs);
                this.enlistRMs.put(xid, resourceManagers);
                this.storeToFile(TM_TRANSACTION_RMs_LOG_FILENAME, this.enlistRMs);
            }
        }

        return true;
	}

	@Override
	public void abort(int xid) throws RemoteException, InvalidTransactionException {
		// TODO Auto-generated method stub

	}


	private void storeToFile(String filePath, Object obj) {
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        ObjectOutputStream oout = null;
        try {
            oout = new ObjectOutputStream(new FileOutputStream(file));
            oout.writeObject(obj);
            oout.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (oout != null)
                    oout.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }
}
