package com.irctc.admin.persistence.dao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.irctc.admin.domain.interfaces.IrPayTransAddInfoRepository;
import com.irctc.admin.domain.interfaces.IrPayTransactionRepository;
import com.irctc.admin.domain.interfaces.IrPgVendorRepository;
import com.irctc.admin.domain.interfaces.IrVendorAppRepository;
import com.irctc.admin.domain.request.DepositSummary;
import com.irctc.admin.domain.request.IrPayTransAddInfo;
import com.irctc.admin.domain.request.IrPayTransaction;
import com.irctc.admin.domain.request.IrPgVendor;
import com.irctc.admin.domain.request.IrPgVendorDetails;
import com.irctc.admin.domain.request.IrVendorApp;
import com.irctc.admin.domain.util.AdminUtil;
import com.irctc.admin.domain.util.MessageConstants;

@Service
public class VendorDAOImpl implements VendorDAO {

	@Autowired
	IrPgVendorRepository vendorRepo;
	@Autowired
	IrPayTransactionRepository transRepo;
	@Autowired
	IrPayTransAddInfoRepository transAddRepo;
	@Autowired
	IrVendorAppRepository vendorAppRepo;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Override
	@Transactional
	public String addFundsToVendorAccount(String vendorCode, String amount,
			String bankName, String refNumber, String ddDate) {
		try {
			if(!isDDNumDuplicate(refNumber)){
				final IrPgVendor vendor = vendorRepo.findVendorByCode(vendorCode);
				if (null != vendor) {
					final long vendorId = vendor.getId();
					final IrVendorApp vendorApp = vendorAppRepo
							.findByAppCodeAndVendorId(vendorId);
					if (null != vendorApp) {
						// Insert into IR_PAY_TRANSACTION
						System.out.println("%%%%%%% == " + vendorApp);
						final long vendorAppId = vendorApp.getId();
						System.out.println("vendorAppId == " + vendorAppId);

						final boolean hasMinBalance = hasMinBalanceForVendor(
								vendorAppId, Long.valueOf(amount));
						System.out.println(hasMinBalance);
						// if(hasMinBalance){
						// Long rdsbal=transRepo.findRDSBalance( vendorAppId,
						// vendorAppId);

						Long rdsbal = findCurrentBalanceForVendor(vendorAppId);
						if (null != rdsbal) {
							rdsbal = rdsbal + Long.valueOf(amount);
						} else {
							rdsbal = Long.valueOf(amount);
						}

						final IrPayTransaction trans = new IrPayTransaction();
						trans.setId(getPayTransactionSeq());
						trans.setAccountNumber(vendorAppId);
						System.out
						.println("---------- " + trans.getAccountNumber());
						trans.setAmountCr(Long.valueOf(amount));
						trans.setDescription(bankName + ":" + refNumber + ":"
								+ ddDate);
						trans.setRefTranId(generateTransactionSequenceNo());
						trans.setStatus("5");
						trans.setTxnType("4");
						trans.setRdsBal(Long.valueOf(rdsbal));
						trans.setCreatedOn(new Date());
						transRepo.save(trans);

						// Insert into IR_PAY_TRANS_ADD_INFO

						final long transId = transRepo
								.findByAccNoAndAmtAndRefTranId(
										Long.valueOf(vendorAppId),
										Long.valueOf(amount), trans.getRefTranId());
						System.out.println("transId == " + transId);
						if (transId > 0) {
							final IrPayTransAddInfo transAddInfo = new IrPayTransAddInfo();
							transAddInfo.setId(getPayTransactionAddInfoSeq());
							transAddInfo.setTranId(transId);
							transAddInfo.setPayEntityId(vendor.getId());
							transAddInfo.setAttribute1(ddDate.toString());
							transAddInfo.setAttribute4(bankName);
							transAddInfo.setAttribute10(refNumber);
							transAddRepo.save(transAddInfo);

							// Update IR_VENDOR_APPS
							vendorApp.setAmount(vendorApp.getAmount()
									+ Long.valueOf(amount));
							vendorAppRepo.save(vendorApp);

							if (vendorApp.getMinAmount() < vendorApp.getAmount()
									+ Long.valueOf(amount)) {
								System.out
								.println("Update active flag starts starts");
								final String updateSql = "UPDATE IR_PAY_ENTITIES SET ACTIVE_FLAG=1 WHERE ACCOUNT_NUMBER=?";
								final int count = jdbcTemplate.update(updateSql,
										new Object[] { vendorAppId });
								System.out.println("update count == " + count);
							}

							return MessageConstants.success;
						}
						return MessageConstants.AMOUNT_LESS_THAN_MIN_BAL;
					}
					return MessageConstants.NO_DATA_FOUND;
				}
				return MessageConstants.VENDOR_CODE_NOT_FOUND;
			}

			return MessageConstants.DUPLICATE_REF_NUMBER;

		} catch (final Exception e) {
			e.printStackTrace();
			return MessageConstants.failure;
		}
	}

	private boolean isDDNumDuplicate(String refNumber) {
		final String str = "select id from IR_PAY_TRANSACTIONS_ADDITIONAL_INFO where attribute10 = ?";
		final List<Map<String, Object>> txnIdList = jdbcTemplate.queryForList(str,new Object[]{refNumber});
		System.out.println("txnId isDDNumDuplicate == "+ txnIdList);
		if(null != txnIdList && txnIdList.size()>0){
			return true;
		}
		return false;
	}

	@Override
	public String addVendor(IrPgVendor vendor) {
		if (null != vendor) {
			if (null != vendor.getCode() && !vendor.getCode().isEmpty()) {
				final IrPgVendor obj = findVendorByCode(vendor.getCode());
				if (null == obj) {
					final IrPgVendorDetails details = vendor.getVendorDetails();
					if (null != details) {
						details.setCreatedBy(123L);// TODO set user id
						details.setCreatedOn(new Date());
						details.setServerIp(AdminUtil.getIpAddress());
						vendor.setVendorDetails(details);
						vendorRepo.save(vendor);
						return MessageConstants.success;
					} else {
						return MessageConstants.INPUT_OBJ_IS_NULL;
					}
				} else {
					return MessageConstants.VENDOR_ALREADY_EXISTS;
				}

			} else {
				return MessageConstants.VENDOR_CODE_NOT_FOUND;
			}
		} else {
			return MessageConstants.INPUT_OBJ_IS_NULL;
		}
	}

	@Override
	public String changeStatus(String code, boolean status) {
		final IrPgVendor obj = findVendorByCode(code);
		if (null != obj) {
			if (obj.isStatus() == status) {
				return MessageConstants.VENDOR_ALREADY_IN_STATE_REQUESTED;
			} else {
				obj.setStatus(status);
				vendorRepo.save(obj);
				// TODO : Update IR_PAYMENT_ENTITES SET UPDATE ACTIVE_FLAG=1
				// WHERE ACCOUNT_NUMBER=?
				return MessageConstants.success;
			}
		} else {
			return MessageConstants.VENDOR_CODE_NOT_FOUND;
		}
	}

	@Override
	public String editVendor(IrPgVendor vendor) {
		if (null != vendor) {
			if (null != vendor.getCode() && !vendor.getCode().isEmpty()) {
				final IrPgVendor obj = findVendorByCode(vendor.getCode());
				if (null != obj) {
					System.out.println("password == " + obj.getPassword());
					final IrPgVendorDetails details = obj.getVendorDetails();
					if (null != details) {
						details.setLastUpdatedBy(123L);// TODO set user id
						details.setLastUpdatedOn(new Date());
						details.setServerIp(AdminUtil.getIpAddress());
						vendor.setVendorDetails(details);
						vendorRepo.save(vendor);
						System.out.println("before save password == "
								+ vendor.getPassword());
						return MessageConstants.success;
					} else {
						return MessageConstants.INPUT_OBJ_IS_NULL;
					}
				} else {
					return MessageConstants.VENDOR_CODE_NOT_FOUND;
				}
			} else {
				return MessageConstants.VENDOR_CODE_NOT_FOUND;
			}
		} else {
			return MessageConstants.INPUT_OBJ_IS_NULL;
		}
	}

	private Long findCurrentBalanceForVendor(long vendorAppId) {
		final String sql = "select amount from ir_vendor_apps where id=?";
		return jdbcTemplate.queryForObject(sql, new Object[] { vendorAppId },
				Long.class);
	}

	@Override
	public IrPgVendor findVendorByCode(String code) {
		return vendorRepo.findVendorByCode(code);
	}

	@Override
	public Iterable<IrPgVendor> findVendorByCodeForPGList(String code) {
		return vendorRepo.findVendorByCodeForPGList(code);
	}

	public String generateTransactionSequenceNo() {
		final String str = "select IR_PAY_TRANS_REF_ID_ADD_PAY.nextval as num from dual";
		final Object[] bm = null;
		final String accNo = jdbcTemplate.queryForObject(str, bm, String.class);
		System.out.println("account number generateTransactionSequenceNo == "
				+ accNo);
		return accNo;
	}

	@Override
	public Iterable<IrPgVendor> getAllVendors() {
		return vendorRepo.findAll();
	}

	@Override
	public List<Map<String, Object>> getDepositSummary(DepositSummary ds) {
		String param = "";
		String sql = null;
		List<Map<String, Object>> list = null;
		if (null != ds.getVendorId() && !ds.getVendorId().isEmpty()) {
			sql = "SELECT  IR_PAY_TRANSACTIONS.ID,IR_PAY_TRANSACTIONS.CREATED_ON,REF_TRAN_ID,AMOUNT_CR,"
					+ " (select b.CODE  from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) as ACCOUNT_NUMBER,"
					+ " TO_CHAR(CREATED_ON,'DD-MM-YYYY HH24:MI')  AS TRANSACTION_DATE,DESCRIPTION "
					+ " FROM IR_PAY_TRANSACTIONS,IR_PAY_TRANSACTIONS_ADDITIONAL_INFO WHERE  IR_PAY_TRANSACTIONS.TXN_TYPE='4'"
					+ " and IR_PAY_TRANSACTIONS.account_number="
					+ " to_char((select a.id  from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) ) "
					+ " and IR_PAY_TRANSACTIONS.ID=IR_PAY_TRANSACTIONS_ADDITIONAL_INFO.TRAN_ID ";

			param = ds.getVendorId() + "," + ds.getVendorId();
		} else {
			sql = "select IR_PAY_TRANSACTIONS.ID,IR_PAY_TRANSACTIONS.CREATED_ON,REF_TRAN_ID,AMOUNT_CR,"
					+ "b.CODE as ACCOUNT_NUMBER,TO_CHAR(CREATED_ON,'DD-MM-YYYY HH24:MI')  AS TRANSACTION_DATE,"
					+ "DESCRIPTION  FROM IR_PAY_TRANSACTIONS,IR_PAY_TRANSACTIONS_ADDITIONAL_INFO ,"
					+ "IR_VENDOR_APPS a,IR_PG_VENDORS b WHERE a.VENDOR_ID=b.id and IR_PAY_TRANSACTIONS.account_number=a.id "
					+ "and IR_PAY_TRANSACTIONS.TXN_TYPE='4' and IR_PAY_TRANSACTIONS.ID=IR_PAY_TRANSACTIONS_ADDITIONAL_INFO.TRAN_ID  ";
		}

		if (ds.getTransactionId() != null && !ds.getTransactionId().isEmpty()) {
			sql = sql + " AND IR_PAY_TRANSACTIONS.REF_TRAN_ID=?";
			param = param + "," + ds.getTransactionId();
		}

		if (ds.getStartDate() != null && !ds.getStartDate().isEmpty()
				&& ds.getEndDate() != null && !ds.getEndDate().isEmpty()) {
			sql = sql
					+ "AND  trunc(IR_PAY_TRANSACTIONS.CREATED_ON) "
					+ "BETWEEN TO_DATE(?,'DD-MM-YYYY') AND TO_DATE(?,'DD-MM-YYYY')";
			param = param + "," + ds.getStartDate() + "," + ds.getEndDate();
		}
		sql = sql + " ORDER BY IR_PAY_TRANSACTIONS.CREATED_ON DESC";
		if (param.startsWith(",")) {
			param = param.substring(1, param.length());
		}
		System.out.println(sql + " || " + param);
		final String[] paramArr = param.split(",");
		System.out.println("************* " + sql);

		if (null != paramArr && paramArr.length == 1 && paramArr[0].equals("")) {
			list = jdbcTemplate.queryForList(sql.toString(), new Object[] {});
		} else {
			final Object[] obj = new Object[paramArr.length];
			System.arraycopy(paramArr, 0, obj, 0, paramArr.length);
			list = jdbcTemplate.queryForList(sql.toString(), obj);
		}
		return list;

	}

	private Long getPayTransactionAddInfoSeq() {
		Long seq = null;
		try {
			final String sql = "select IR_PAY_TRANS_ADD_INFO_SYNO_SEQ.nextval from dual";
			final Object[] bm = null;
			seq = jdbcTemplate.queryForObject(sql, bm, Long.class);
			System.out.println("getPayTransactionAddInfoSeq == " + seq);
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return seq;

	}

	private Long getPayTransactionSeq() {
		Long seq = null;
		try {
			final String sql = "select IR_PAY_TRANS_SYNO_SEQ.nextval from dual";
			final Object[] bm = null;
			seq = jdbcTemplate.queryForObject(sql, bm, Long.class);
			System.out.println("getPayTransactionSeq == " + seq);
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return seq;

	}

	@Override
	public List<Map<String, Object>> getVendorCancellationReport(
			String vendorCode, String fromDate, String toDate) {
		String sql = null;
		List<Map<String, Object>> result = null;
		Object[] obj = null;
		try {
			if (null != vendorCode && !vendorCode.isEmpty()) {
				/*
				 * sql=
				 * " select DISTINCT f.code as payment_gateway, e.TRANSACTIONID,l.value as Refund_status,IRFBTC.amount_refund as refund_amount,e.settlement_id,TO_CHAR(e.refund_date,'DD-MM-YYYY') REFUND_DATE,"
				 * + " j.VALUE as STATUS,d.APP_CODE,d.RDS_ACC_BALANCE," +
				 * "k.VALUE as TXN_TYPE,IRFBTC.Cancellation_ID ,TO_CHAR(IRFBTC.Cancellation_Date,'dd-mm-yyyy hh24:mi') Cancellation_Date,IRFBT.TOTAL_CHARGE "
				 * +
				 * "from ir_pay_transactions d ,ir_refund e, BV_ENUM_VALUES j,BV_ENUM_VALUES k,bv_enum_values l,IR_FB_TICKET_CANCEL IRFBTC,IR_FB_TRANSACTION IRFBT,ir_vendor_apps e,ir_pg_vendors f "
				 * + "where f.id=e.vendor_id and e.id=d.account_number and " +
				 * " account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) and d.TXN_TYPE in ('1','2') "
				 * +
				 * "and j.INT_CODE=d.STATUS and j.TYPE_NAME='PAYMENT_STATUS' and k.INT_CODE=d.TXN_TYPE and k.TYPE_NAME='TRANSACTION_TYPE' and e.TRANSACTIONID=d.REF_TRAN_ID "
				 * +
				 * " and l.TYPE_NAME='REFUNDSTATUS' and l.INT_CODE=e.refund_status AND d.REF_TRAN_ID = IRFBTC.TRANSACTION_ID(+) "
				 * +
				 * "AND IRFBTC.TRANSACTION_ID = IRFBT.TRANSACTION_ID(+) and trunc(d.CREATED_ON) between to_date(?,'dd-mm-yyyy') and to_date(?,'dd-mm-yyyy')"
				 * + " order by e.TRANSACTIONID desc";
				 */

				sql = "SELECT DISTINCT NVL(X.RECEIPT_NUMBER,'') as REFUND_SETTLEMENT_ID,TO_CHAR(X.REFUND_DATE,'DD-MM-YYYY hh24:mi') REFUND_TABLE_DATE,"
						+ " TO_CHAR(X.ACTUAL_REFUND_DATE,'DD-MM-YYYY hh24:mi') ACTUAL_REFUND_DATE, Y.attribute4 as PAYMENT_MODE,X.payment_gateway,"
						+ " X.TRANSACTIONID,X.Refund_status,NVL(X.refund_amount,Z.TOTAL_CHARGE) refund_amount,X.settlement_id , X.RDS_REFUND_DATE REFUND_DATE,"
						+ " X.STATUS,X.APP_CODE,X.TXN_TYPE,NVL(X.TOTAL_CHARGE,Z.TOTAL_CHARGE) TOTAL_CHARGE  "
						+ " FROM (SELECT DISTINCT AB.RECEIPT_NUMBER,AB.REFUND_DATE, AB.ACTUAL_REFUND_DATE, AB.payment_gateway, AB.TRANSACTIONID,"
						+ " AB.Refund_status, SUM(AB.refund_amount) refund_amount, AB.settlement_id, AB.RDS_REFUND_DATE, AB.STATUS, AB.APP_CODE,"
						+ " AB.TXN_TYPE,  AB.TOTAL_CHARGE FROM (SELECT DISTINCT e.RECEIPT_NUMBER,e.REFUND_DATE, e.ACTUAL_REFUND_DATE,"
						+ " f.code AS payment_gateway, e.TRANSACTIONID,  l.value AS Refund_status, e.refund_amount AS refund_amount,"
						+ "NVL(IRFBT.settlement_id,0) settlement_id , TO_CHAR(d.CREATED_ON,'DD-MM-YYYY hh24:mi') RDS_REFUND_DATE,  j.VALUE AS STATUS , "
						+ " d.APP_CODE , (  CASE    WHEN k.VALUE='Cancel_Refund'    THEN 'C'    ELSE 'R'  END) AS TXN_TYPE,  "
						+ "   IRFBT.TOTAL_CHARGE FROM ir_pay_transactions d , "
						+ " ir_refund e,  BV_ENUM_VALUES j,  BV_ENUM_VALUES k,  bv_enum_values l, IR_FB_TICKET_CANCEL IRFBTC,  IR_FB_TRANSACTION IRFBT, "
						+ " ir_vendor_apps XY,  ir_pg_vendors f WHERE 1=1 AND f.id=xy.vendor_id AND xy.id=d.account_number "
						+ " AND account_number = (SELECT a.id  FROM IR_VENDOR_APPS a, IR_PG_VENDORS b  WHERE a.VENDOR_ID=b.id  AND b.CODE=?) "
						+ "AND d.TXN_TYPE IN ('1','2')  AND j.INT_CODE=d.STATUS AND j.TYPE_NAME='PAYMENT_STATUS' AND k.INT_CODE=d.TXN_TYPE AND "
						+ "k.TYPE_NAME='TRANSACTION_TYPE' AND e.TRANSACTIONID=d.REF_TRAN_ID AND l.TYPE_NAME='REFUNDSTATUS' AND l.INT_CODE=e.refund_status "
						+ "AND d.REF_TRAN_ID= IRFBTC.TRANSACTION_ID(+) AND IRFBTC.TRANSACTION_ID = IRFBT.TRANSACTION_ID(+) AND TRUNC(d.CREATED_ON) "
						+ "BETWEEN to_date(?,'dd-mm-yyyy') AND to_date(?,'dd-mm-yyyy')  GROUP BY e.RECEIPT_NUMBER,e.REFUND_DATE,"
						+ " e.ACTUAL_REFUND_DATE,f.code, e.TRANSACTIONID, l.value, e.refund_amount, IRFBT.settlement_id , TO_CHAR(d.CREATED_ON,'DD-MM-YYYY hh24:mi'),"
						+ "j.VALUE, d.APP_CODE, k.VALUE,  IRFBT.TOTAL_CHARGE) AB GROUP BY AB.RECEIPT_NUMBER, "
						+ "AB.REFUND_DATE, AB.ACTUAL_REFUND_DATE,payment_gateway,TRANSACTIONID, Refund_status, settlement_id, RDS_REFUND_DATE, STATUS,"
						+ " APP_CODE, TXN_TYPE, TOTAL_CHARGE) X , (SELECT DISTINCT B.REF_TRAN_ID, B.ID, A.attribute4 "
						+ "FROM IR_PAY_TRANSACTIONS_ADDITIONAL_INFO A ,ir_pay_transactions B WHERE B.id=A.TRAN_ID AND B.TXN_TYPE='0') Y,"
						+ "(SELECT DISTINCT TRANSACTION_ID,TOTAL_CHARGE FROM IR_FB_TRANSACTION) Z  WHERE Y.REF_TRAN_ID = X.TRANSACTIONID "
						+ "AND X.TRANSACTIONID = Z.TRANSACTION_ID order by X.RDS_REFUND_DATE desc";
//				sql	=	"SELECT DISTINCT e.RECEIPT_NUMBER ,e.REFUND_DATE, e.ACTUAL_REFUND_DATE, f.code AS payment_gateway,e.TRANSACTIONID,"
//						+ "l.value AS Refund_status,D.AMOUNT_CR refund_amount,NVL(IRFBT.settlement_id,0) settlement_id,"
//						+ "TO_CHAR(d.CREATED_ON,'DD-MM-YYYY hh24:mi') RDS_REFUND_DATE,j.VALUE AS STATUS,  d.APP_CODE,"
//						+ "(  CASE    WHEN k.VALUE='Cancel_Refund'    THEN 'C'    ELSE 'R'  END) AS TXN_TYPE,IRFBT.TOTAL_CHARGE "
//						+ " FROM ir_pay_transactions d ,ir_refund e ,ir_pg_vendors f,BV_ENUM_VALUES j,BV_ENUM_VALUES k,bv_enum_values l,IR_FB_TICKET_CANCEL IRFBTC,"
//						+ " IR_FB_TRANSACTION IRFBT,ir_vendor_apps XY WHERE f.id=xy.vendor_id AND xy.id=d.account_number "
//						+ " AND account_number=(SELECT a.id FROM IR_VENDOR_APPS a,IR_PG_VENDORS b WHERE a.VENDOR_ID=b.id AND b.CODE=?) "
//						+ " AND d.TXN_TYPE IN ('1','2')  AND j.INT_CODE=d.STATUS AND j.TYPE_NAME='PAYMENT_STATUS' AND k.INT_CODE=d.TXN_TYPE AND k.TYPE_NAME='TRANSACTION_TYPE' "
//						+ " AND e.TRANSACTIONID=d.REF_TRAN_ID AND l.TYPE_NAME='REFUNDSTATUS' AND l.INT_CODE=e.refund_status AND d.REF_TRAN_ID= IRFBTC.TRANSACTION_ID(+) "
//						+ "  AND IRFBTC.TRANSACTION_ID = IRFBT.TRANSACTION_ID(+) AND TRUNC(d.CREATED_ON) BETWEEN to_date(?,'dd-mm-yyyy') AND to_date(?,'dd-mm-yyyy') "
//						+ " AND D.RDS_ACC_BALANCE IS NOT NULL AND d.AMOUNT_CR=E.REFUND_AMOUNT and D.status='5' UNION ALL SELECT E.RECEIPT_NUMBER ,E.REFUND_DATE , "
//						+ " E.ACTUAL_REFUND_DATE , F.code AS payment_gateway ,E.TRANSACTIONID ,L.value AS Refund_status ,E.REFUND_AMOUNT ,NVL(IRFBT.settlement_id,0) settlement_id ,"
//						+ " TO_CHAR(D.CREATED_ON,'DD-MM-YYYY hh24:mi') RDS_REFUND_DATE ,J.VALUE AS STATUS ,  D.APP_CODE,(  CASE    WHEN K.VALUE='Cancel_Refund'    THEN 'C'    ELSE 'R'  END) AS TXN_TYPE ,"
//						+ " IRFBT.TOTAL_CHARGE from IR_PAY_TRANSACTIONS D ,ir_refund e,ir_pg_vendors f,BV_ENUM_VALUES j,BV_ENUM_VALUES k,bv_enum_values l,IR_FB_TRANSACTION IRFBT ,"
//						+ " ir_vendor_apps XYwhere  D.Account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) and D.status='5' "
//						+ " and txn_type in ('1','2') AND RDS_ACC_BALANCE IS NOT NULL AND TRUNC(d.CREATED_ON) BETWEEN to_date(?,'dd-mm-yyyy') AND to_date(?,'dd-mm-yyyy') "
//						+ " AND D.REF_TRAN_ID NOT IN (SELECT DISTINCT REF_TRAN_ID from IR_PAY_TRANSACTIONS D,ir_refund E where  "
//						+ " Account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) and D.status='5'  and txn_type in ('1','2') "
//						+ " AND RDS_ACC_BALANCE IS NOT NULL and trunc(CREATED_ON)=to_date('18-03-18','dd-mm-yy') AND e.TRANSACTIONID=d.REF_TRAN_ID AND d.AMOUNT_CR=E.REFUND_AMOUNT)"
//						+ " AND e.TRANSACTIONID=d.REF_TRAN_ID AND f.id=xy.vendor_id AND xy.id=d.account_number   AND j.INT_CODE=d.STATUS AND j.TYPE_NAME='PAYMENT_STATUS' "
//						+ " AND k.INT_CODE=d.TXN_TYPE AND k.TYPE_NAME='TRANSACTION_TYPE' AND l.TYPE_NAME='REFUNDSTATUS' AND l.INT_CODE=e.refund_status AND IRFBT.TRANSACTION_ID=E.TRANSACTIONID"; 
				obj = new Object[3];
				obj[0] = vendorCode;
				obj[1] = fromDate;
				obj[2] = toDate;
			} else {
				/*
				 * sql=
				 * " select DISTINCT f.code as payment_gateway,e.TRANSACTIONID,l.value as Refund_status,IRFBTC.amount_refund as refund_amount,e.settlement_id,TO_CHAR(e.refund_date,'DD-MM-YYYY') REFUND_DATE,"
				 * + " j.VALUE as STATUS,d.APP_CODE,d.RDS_ACC_BALANCE," +
				 * "k.VALUE as TXN_TYPE,IRFBTC.Cancellation_ID ,TO_CHAR(IRFBTC.Cancellation_Date,'dd-mm-yyyy') Cancellation_Date,IRFBT.TOTAL_CHARGE "
				 * +
				 * "from ir_pay_transactions d ,ir_refund e, BV_ENUM_VALUES j,BV_ENUM_VALUES k,bv_enum_values l,IR_FB_TICKET_CANCEL IRFBTC,IR_FB_TRANSACTION IRFBT,ir_vendor_apps e,ir_pg_vendors f "
				 * +
				 * "where f.id=e.vendor_id and e.id=d.account_number and   d.TXN_TYPE in ('1','2') "
				 * +
				 * "and j.INT_CODE=d.STATUS and j.TYPE_NAME='PAYMENT_STATUS' and k.INT_CODE=d.TXN_TYPE and k.TYPE_NAME='TRANSACTION_TYPE' and e.TRANSACTIONID=d.REF_TRAN_ID "
				 * +
				 * " and l.TYPE_NAME='REFUNDSTATUS' and l.INT_CODE=e.refund_status AND d.REF_TRAN_ID = IRFBTC.TRANSACTION_ID(+) "
				 * +
				 * "AND IRFBTC.TRANSACTION_ID = IRFBT.TRANSACTION_ID(+) and trunc(d.CREATED_ON) between to_date(?,'dd-mm-yyyy') and to_date(?,'dd-mm-yyyy')"
				 * + " order by e.TRANSACTIONID desc";
				 */

				sql = "	select DISTINCT D.ID, f.code as payment_gateway,e.TRANSACTIONID "
						+ " ,l.value as Refund_status,sum(IRFBTC.amount_refund) as refund_amount "
						+ " ,NVL(e.settlement_id,0) settlement_id "
						+ " ,TO_CHAR(e.refund_date,'DD-MM-YYYY hh24:mi:ss') REFUND_DATE "
						+ " ,j.VALUE as STATUS,d.APP_CODE,d.RDS_ACC_BALANCE ,k.VALUE as TXN_TYPE "
						+ " ,TO_CHAR(IRFBTC.Cancellation_Date,'dd-mm-yyyy') Cancellation_Date ,IRFBT.TOTAL_CHARGE "
						+ " from ir_pay_transactions d ,ir_refund e, BV_ENUM_VALUES j,BV_ENUM_VALUES k,bv_enum_values l,IR_FB_TICKET_CANCEL IRFBTC,IR_FB_TRANSACTION IRFBT,ir_vendor_apps e,ir_pg_vendors f "
						+ " where f.id=e.vendor_id and e.id=d.account_number and  "
						+ " account_number in (select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id ) and d.TXN_TYPE in ('1','2') "
						+ " and j.INT_CODE=d.STATUS and j.TYPE_NAME='PAYMENT_STATUS' and k.INT_CODE=d.TXN_TYPE and k.TYPE_NAME='TRANSACTION_TYPE' and e.TRANSACTIONID=d.REF_TRAN_ID "
						+ " and l.TYPE_NAME='REFUNDSTATUS' and l.INT_CODE=e.refund_status AND d.REF_TRAN_ID = IRFBTC.TRANSACTION_ID(+) "
						+ " AND IRFBTC.TRANSACTION_ID = IRFBT.TRANSACTION_ID(+) and trunc(d.CREATED_ON) between to_date(?,'dd-mm-yyyy') "
						+ " and to_date(?,'dd-mm-yyyy')  group by D.ID, f.code,e.TRANSACTIONID ,l.value ,e.settlement_id "
						+ " ,TO_CHAR(e.refund_date,'DD-MM-YYYY hh24:mi:ss') ,j.VALUE ,d.APP_CODE,d.RDS_ACC_BALANCE "
						+ " ,k.VALUE ,TO_CHAR(IRFBTC.Cancellation_Date,'dd-mm-yyyy') ,IRFBT.TOTAL_CHARGE ";

				obj = new Object[2];
				obj[0] = fromDate;
				obj[1] = toDate;
			}

			System.out.println(sql);
			result = jdbcTemplate.queryForList(sql, obj);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public List<Map<String, Object>> getVendorCancellationTextReport(
			String vendorCode, String fromDate, String toDate) {
		String sql = null;
		List<Map<String, Object>> result = null;
		Object[] obj = null;
		try {
			if (null != vendorCode && !vendorCode.isEmpty()) {
				sql = "SELECT X.TRANSACTIONID  ||'|'||X.TXN_TYPE  ||'|'||NVL(X.refund_amount,Z.TOTAL_CHARGE)  ||'|'||Y.settlement_id  ||'|'||X.REFUND_DATE  ||'|'||NVL(X.TOTAL_CHARGE,Z.TOTAL_CHARGE) "
						+ " ||'|'||X.REFUNDSEQID FROM (SELECT MAX(AB.REFUNDSEQID) REFUNDSEQID,     AB.TRANSACTIONID , "
						+ "   SUM(AB.refund_amount) refund_amount,     AB.REFUND_DATE , AB.TXN_TYPE,AB.TOTAL_CHARGE   FROM  "
						+ "   (SELECT DISTINCT E.REFUNDSEQID ,e.TRANSACTIONID ,e.refund_amount AS refund_amount ,TO_CHAR(e.refund_date,'yyyymmdd') REFUND_DATE ,"
						+ " (CASE WHEN k.VALUE='Cancel_Refund' THEN 'C' ELSE 'R' END) AS TXN_TYPE ,IRFBT.TOTAL_CHARGE     FROM ir_pay_transactions d ,"
						+ " ir_refund e ,ir_pg_vendors f ,BV_ENUM_VALUES j ,BV_ENUM_VALUES k ,bv_enum_values l ,"
						+ " IR_FB_TICKET_CANCEL IRFBTC ,IR_FB_TRANSACTION IRFBT ,ir_vendor_apps XY     WHERE  f.id=xy.vendor_id  "
						+ " AND xy.id=d.account_number    AND account_number=(SELECT a.id      FROM IR_VENDOR_APPS a,IR_PG_VENDORS b WHERE a.VENDOR_ID=b.id AND b.CODE=?) "
						+ " AND d.TXN_TYPE IN ('1','2')     AND j.INT_CODE=d.STATUS     AND j.TYPE_NAME='PAYMENT_STATUS'     AND k.INT_CODE=d.TXN_TYPE "
						+ " AND k.TYPE_NAME='TRANSACTION_TYPE'     AND e.TRANSACTIONID=d.REF_TRAN_ID     AND l.TYPE_NAME='REFUNDSTATUS'     "
						+ " AND l.INT_CODE=e.refund_status AND d.REF_TRAN_ID= IRFBTC.TRANSACTION_ID(+)     "
						+ " AND IRFBTC.TRANSACTION_ID = IRFBT.TRANSACTION_ID(+) AND TRUNC(d.CREATED_ON) BETWEEN to_date(?,'dd-mm-yyyy') AND to_date(?,'dd-mm-yyyy') GROUP BY e.TRANSACTIONID,"
						+ "e.refund_amount,TO_CHAR(e.refund_date,'yyyymmdd'), k.VALUE,IRFBT.TOTAL_CHARGE,E.REFUNDSEQID) AB   GROUP BY TRANSACTIONID,REFUND_DATE,TXN_TYPE ,TOTAL_CHARGE) X,"
						+ "(SELECT DISTINCT B.REF_TRAN_ID,B.ID,A.attribute4 ,A.attribute10 settlement_id   FROM IR_PAY_TRANSACTIONS_ADDITIONAL_INFO A ,ir_pay_transactions B  WHERE B.id=A.TRAN_ID   AND B.TXN_TYPE='0') Y,"
						+ "(SELECT DISTINCT TRANSACTION_ID,TOTAL_CHARGE FROM IR_FB_TRANSACTION) Z WHERE Y.REF_TRAN_ID = X.TRANSACTIONID AND X.TRANSACTIONID = Z.TRANSACTION_ID ORDER BY X.REFUNDSEQID ";
				obj = new Object[3];
				obj[0] = vendorCode;
				obj[1] = fromDate;
				obj[2] = toDate;
			}

			System.out.println("getVendorCancellationTextReport == " + sql);
			result = jdbcTemplate.queryForList(sql, obj);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public Map<String, Object> getvendordetails(String vendorCode) {
		List<Map<String, Object>> seq = null;
		try {
			final String sql = "select a.amount,b.email_id,b.MOBILE_NO,b.CC_MAIL,c.code from "
					+ " ir_vendor_apps a,ir_pg_vendor_details b,ir_pg_vendors c "
					+ " where a.vendor_id=c.id and b.id=c.vendor_detail_id and c.code=?";
			seq = jdbcTemplate.queryForList(sql, vendorCode);
			System.out.println("getPayTransactionAddInfoSeq == " + seq);
			return seq.get(0);
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public List<Map<String, Object>> getVendorRDSReport(String vendorCode,
			String fromDate, String toDate) {
		System.out.println(vendorCode + " || " + fromDate + " || " + toDate);
		String sql = null;
		List<Map<String, Object>> result = null;
		try {
			sql = "SELECT TO_CHAR(J.ORDER_DATE,'DD-MM-YYYY') ORDER_DATE, J.OPENING_BALANCE , NVL(C.Order_Booked,0) Orders_Booked, NVL(C.Order_Amount,0) Orders_Amount,"
					+ "NVL(D.Order_CANCELLED,0) Orders_CANCELLED,NVL(D.CANCELLATION_AMOUNT,0) CANCELLATION_AMOUNT,NVL(E.AMOUNT_DEPOSITED,0) AMOUNT_DEPOSITED, "
					+ "J.CLOSING_Balance FROM (SELECT A.ORDER_DATE,nvl(B.OPENING_BALANCE,0) OPENING_BALANCE, A.CLOSING_Balance FROM "
					+ "(SELECT Z.ORDER_DATE,Z.CLOSING_Balance FROM (select MAX(ID) ID , TRUNC(created_on) ORDER_DATE from IR_PAY_TRANSACTIONS where "
					+ " ACCOUNT_NUMBER=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) AND RDS_ACC_BALANCE IS NOT NULL GROUP BY  TRUNC(created_on) ) Y,"
					+ "(select id ,RDS_ACC_BALANCE CLOSING_Balance, TRUNC(created_on) ORDER_DATE from IR_PAY_TRANSACTIONS where 1=1  AND RDS_ACC_BALANCE IS NOT NULL "
					+ " AND ACCOUNT_NUMBER=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?)) Z "
					+ "WHERE Y.ORDER_DATE = Z.ORDER_DATE AND Y.ID = Z.ID ) A,(SELECT V.ORDER_DATE,V.OPENING_BALANCE FROM (select MAX(ID) ID, TRUNC(created_on) ACTUAL_DATE from IR_PAY_TRANSACTIONS "
					+ "where RDS_ACC_BALANCE IS NOT NULL AND ACCOUNT_NUMBER=(select a.id from IR_VENDOR_APPS a ,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) GROUP BY  TRUNC(created_on) ) U, "
					+ "(select id ,RDS_ACC_BALANCE OPENING_BALANCE,TRUNC(created_on) ACTUAL_DATE , TRUNC((created_on+1)) ORDER_DATE from IR_PAY_TRANSACTIONS "
					+ "where RDS_ACC_BALANCE IS NOT NULL AND ACCOUNT_NUMBER=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?)) V "
					+ "WHERE U.ACTUAL_DATE = V.ACTUAL_DATE AND U.ID = V.ID) B WHERE 1=1 AND A.ORDER_DATE = B.ORDER_DATE(+) ) J ,(SELECT TRUNC(created_on) Order_Date ,count(id) Order_Booked ,"
					+ "sum(amount_dr) Order_Amount from IR_PAY_TRANSACTIONS where 1=1 and Account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) "
					+ "and status='5' and txn_type in ('0','3') AND RDS_ACC_BALANCE IS NOT NULL group by TRUNC(created_on)) C,(SELECT TRUNC(created_on) Order_Date ,count(id) Order_CANCELLED ,"
					+ " sum(AMOUNT_CR) CANCELLATION_AMOUNT from IR_PAY_TRANSACTIONS where  Account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) "
					+ "and status='5' and txn_type in ('1','2') AND RDS_ACC_BALANCE IS NOT NULL group by TRUNC(created_on)) D, (SELECT TRUNC(created_on) Order_Date ,SUM(NVL(AMOUNT_CR,0)) AMOUNT_DEPOSITED "
					+ "from IR_PAY_TRANSACTIONS where  Account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) and status='5' and txn_type = '4' "
					+ "AND RDS_ACC_BALANCE IS NOT NULL group by TRUNC(created_on)) E WHERE  1=1 "
					+ "and J.ORDER_DATE = C.ORDER_DATE(+) AND J.ORDER_DATE = D.ORDER_DATE(+) AND J.ORDER_DATE = E.ORDER_DATE(+) "
					+ " AND J.ORDER_DATE BETWEEN TO_DATE(?,'DD-MM-YY') AND TO_DATE(?,'DD-MM-YY') ORDER BY J.ORDER_DATE";
			final Object[] obj = new Object[9];
			obj[0] = vendorCode;
			obj[1] = vendorCode;
			obj[2] = vendorCode;
			obj[3] = vendorCode;
			obj[4] = vendorCode;
			obj[5] = vendorCode;
			obj[6] = vendorCode;
			obj[7] = fromDate;
			obj[8] = toDate;
			System.out.println(sql);
			result = jdbcTemplate.queryForList(sql, obj);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public List<Map<String, Object>> getVendorTransactionDetailReport(
	String vendorCode, String fromDate, String toDate,String txnId) {
	System.out.println(vendorCode + " || " + fromDate + " || " + toDate);
	String sql = null;
	List<Map<String, Object>> result = null;
	try {

	if(null != txnId && !txnId.isEmpty()){
	sql = "SELECT ipta.attribute9 as settlement_date,PT.ID as RDS_ID , NVL(PT.AMOUNT_DR,0) as AMOUNT_DR, NVL(PT.AMOUNT_CR,0) as AMOUNT_CR,"
	+ "(CASE WHEN AMOUNT_DR>0 THEN NVL(PT.RDS_ACC_BALANCE,0)+PT.AMOUNT_DR WHEN AMOUNT_CR>0 THEN NVL(PT.RDS_ACC_BALANCE,0)-PT.AMOUNT_CR "
	+ " WHEN PT.STATUS IN ('4','6')THEN NVL(PT.RDS_ACC_BALANCE,0) END) "
	+ " as OPENING_BALANCE,"
	+ " NVL(PT.RDS_ACC_BALANCE,0) as CLOSING_BALANCE,"
	+ " EV.VALUE as PG_STATUS, DECODE(PT.AMOUNT_DR,0,'CREDIT',NULL,'CREDIT','DEBIT') as TRANSACTION_MODE, "
	+ " PT.REF_TRAN_ID as TRANSACTION_ID,PGV.CODE as PAYMENT_GATEWAY,nvl(IPTA.ATTRIBUTE10,' ') as BANK_SETTLEMENT_ID,"
	+ " EV1.VALUE as AIRLINE_STATUS, nvl(IPTA.ATTRIBUTE4,' ') as PAYMENT_MODE,to_char(PT.CREATED_ON,'dd/mm/yyyy hh24:mi:ss') as TRANSACTION_DATE "
	+ " FROM IR_PAY_TRANSACTIONS PT,IR_PAY_TRANSACTIONS_ADDITIONAL_INFO IPTA,BV_ENUM_VALUES EV,BV_ENUM_VALUES EV1,IR_VENDOR_APPS VA,IR_PG_VENDORS PGV "
	+ " WHERE PT.STATUS=EV.INT_CODE AND PT.ID=IPTA.TRAN_ID(+) AND TO_CHAR(EV1.INT_CODE)=PT.TXN_TYPE "
	+ " AND EV1.TYPE_NAME='TRANSACTION_TYPE' AND EV.TYPE_NAME='PAYMENT_STATUS' "
	+ " AND VA.VENDOR_ID=PGV.ID AND VA.ID=PT.ACCOUNT_NUMBER AND PT.RDS_ACC_BALANCE IS NOT NULL"
	+ " AND PT.REF_TRAN_ID=? ORDER BY PT.ID DESC";
	final Object[] obj = new Object[1];
	obj[0] = txnId;
	result = jdbcTemplate.queryForList(sql, obj);
	}
	else if(null != fromDate && !fromDate.isEmpty() && null != toDate && !toDate.isEmpty() && null != vendorCode && !vendorCode.isEmpty()){
	sql = "SELECT ipta.attribute9 as settlement_date,PT.ID as RDS_ID , NVL(PT.AMOUNT_DR,0) as AMOUNT_DR, NVL(PT.AMOUNT_CR,0) as AMOUNT_CR,"
	+ "(CASE WHEN AMOUNT_DR>0 THEN NVL(PT.RDS_ACC_BALANCE,0)+PT.AMOUNT_DR WHEN AMOUNT_CR>0 THEN NVL(PT.RDS_ACC_BALANCE,0)-PT.AMOUNT_CR END) "
	+ " as OPENING_BALANCE,"
	+ " NVL(PT.RDS_ACC_BALANCE,0) as CLOSING_BALANCE,"
	+ " EV.VALUE as PG_STATUS, DECODE(PT.AMOUNT_DR,0,'CREDIT',NULL,'CREDIT','DEBIT') as TRANSACTION_MODE, "
	+ " PT.REF_TRAN_ID as TRANSACTION_ID,PGV.CODE as PAYMENT_GATEWAY,nvl(IPTA.ATTRIBUTE10,' ') as BANK_SETTLEMENT_ID,"
	+ " EV1.VALUE as AIRLINE_STATUS, nvl(IPTA.ATTRIBUTE4,' ') as PAYMENT_MODE,to_char(PT.CREATED_ON,'dd/mm/yyyy hh24:mi:ss') as TRANSACTION_DATE "
	+ " FROM IR_PAY_TRANSACTIONS PT,IR_PAY_TRANSACTIONS_ADDITIONAL_INFO IPTA,BV_ENUM_VALUES EV,BV_ENUM_VALUES EV1,IR_VENDOR_APPS VA,IR_PG_VENDORS PGV "
	+ " WHERE PT.STATUS=EV.INT_CODE AND PT.ID=IPTA.TRAN_ID(+) AND TO_CHAR(EV1.INT_CODE)=PT.TXN_TYPE "
	+ " AND EV1.TYPE_NAME='TRANSACTION_TYPE' AND EV.TYPE_NAME='PAYMENT_STATUS' "
	+ " AND VA.VENDOR_ID=PGV.ID AND VA.ID=PT.ACCOUNT_NUMBER AND PT.RDS_ACC_BALANCE IS NOT NULL"
	+ " AND PT.ACCOUNT_NUMBER=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) "
	+ " AND trunc(PT.CREATED_ON) between to_date(?,'dd-mm-yyyy') and to_date(?,'dd-mm-yyyy') ORDER BY PT.ID DESC";
	final Object[] obj = new Object[3];
	obj[0] = vendorCode;
	obj[1] = fromDate;
	obj[2] = toDate;
	result = jdbcTemplate.queryForList(sql, obj);
	}


	else {
	sql = "SELECT PT.ID as RDS_ID , NVL(PT.AMOUNT_DR,0) as AMOUNT_DR, NVL(PT.AMOUNT_CR,0) as AMOUNT_CR,"
	+ "(CASE WHEN AMOUNT_DR>0 THEN NVL(PT.RDS_ACC_BALANCE,0)+PT.AMOUNT_DR WHEN AMOUNT_CR>0 THEN NVL(PT.RDS_ACC_BALANCE,0)-PT.AMOUNT_CR END) "
	+ " as OPENING_BALANCE,"
	+ " NVL(PT.RDS_ACC_BALANCE,0) as CLOSING_BALANCE,"
	+ " EV.VALUE as PG_STATUS, DECODE(PT.AMOUNT_DR,0,'CREDIT',NULL,'CREDIT','DEBIT') as TRANSACTION_MODE, "
	+ " PT.REF_TRAN_ID as TRANSACTION_ID,PGV.CODE as PAYMENT_GATEWAY,nvl(IPTA.ATTRIBUTE10,' ') as BANK_SETTLEMENT_ID,"
	+ " EV1.VALUE as AIRLINE_STATUS, nvl(IPTA.ATTRIBUTE4,' ') as PAYMENT_MODE,to_char(PT.CREATED_ON,'dd/mm/yyyy hh24:mi:ss') as TRANSACTION_DATE "
	+ " FROM IR_PAY_TRANSACTIONS PT,IR_PAY_TRANSACTIONS_ADDITIONAL_INFO IPTA,BV_ENUM_VALUES EV,BV_ENUM_VALUES EV1,IR_VENDOR_APPS VA,IR_PG_VENDORS PGV "
	+ " WHERE PT.STATUS=EV.INT_CODE AND PT.ID=IPTA.TRAN_ID(+) AND TO_CHAR(EV1.INT_CODE)=PT.TXN_TYPE "
	+ " AND EV1.TYPE_NAME='TRANSACTION_TYPE' AND EV.TYPE_NAME='PAYMENT_STATUS' "
	+ " AND VA.VENDOR_ID=PGV.ID AND VA.ID=PT.ACCOUNT_NUMBER AND PT.RDS_ACC_BALANCE IS NOT NULL"
	+ " AND PT.STATUS='5' AND trunc(PT.CREATED_ON) between to_date(?,'dd-mm-yyyy') and to_date(?,'dd-mm-yyyy') ORDER BY PT.ID DESC";
	final Object[] obj = new Object[2];
	obj[0] = fromDate;
	obj[1] = toDate;
	result = jdbcTemplate.queryForList(sql, obj);
	}
	System.out.println(sql);

	} catch (final Exception e) {
	e.printStackTrace();
	}
	return result;
	}
	@Override
	public List<Map<String, Object>> getVendorTransactionReport(
			String vendorCode, String fromDate, String toDate) {
		String sql = null;
		List<Map<String, Object>> result = null;
		try {

			if (null != vendorCode && !vendorCode.isEmpty()) {
				if (null != fromDate && !fromDate.isEmpty() && null != toDate
						&& !toDate.isEmpty()) {
					sql = "select f.code as payment_gateway,a.amount_dr as amount,b.attribute4 as payment_mode,b.attribute10 as transaction_id,to_char(a.created_on,'dd/mm/yyyy hh24:mi') as date_of_booking,"
							+ " a.ref_tran_id as order_id,b.attribute4 as payment_mode,"
							+ "d.name as PAYMENT_OPTION_ID , c.VALUE as STATUS from ir_pay_transactions a ,"
							+ " IR_PAY_TRANSACTIONS_ADDITIONAL_INFO b,BV_ENUM_VALUES c,ir_pay_entities d,ir_vendor_apps e,ir_pg_vendors f where f.id=e.vendor_id and e.id=d.account_number and a.id=b.tran_id and  "
							+ " a.Account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where a.VENDOR_ID=b.id and b.CODE=?) and a.STATUS=c.INT_CODE and c.TYPE_NAME='PAYMENT_STATUS'  and a.status='5' "
							+ "and d.id=a.PAYMENT_OPTION_ID and trunc(a.CREATED_ON) between (to_date(?,'DD-MM-YYYY')) AND (to_date(?,'DD-MM-YYYY'))"
							+ " order by a.ref_tran_id desc";
					System.out.println(sql);
					final Object[] obj = new Object[3];
					obj[0] = vendorCode;
					obj[1] = fromDate;
					obj[2] = toDate;
					System.out.println(sql);
					result = jdbcTemplate.queryForList(sql, obj);
				}
				sql = "select amount from ir_vendor_apps where vendor_id=(select id from ir_pg_vendors where code=?)";
				final Map<String, Object> amtList = jdbcTemplate.queryForMap(
						sql, new Object[] { vendorCode });
				result.add(amtList);
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public List<Map<String, Object>> getVendorTransactionTextReport(
			String vendorCode, String fromDate, String toDate) {
		String sql = null;
		List<Map<String, Object>> result = null;
		try {

			if (null != vendorCode && !vendorCode.isEmpty()) {
				if (null != fromDate && !fromDate.isEmpty() && null != toDate
						&& !toDate.isEmpty()) {
					sql = "select b.attribute10||'|'||a.amount_dr||'|'||to_char(a.created_on,'yyyymmddhh24miss')||'|'||a.ref_tran_id  as data"
							+ " from ir_pay_transactions a , IR_PAY_TRANSACTIONS_ADDITIONAL_INFO b,BV_ENUM_VALUES c,"
							+ "ir_pay_entities d,ir_vendor_apps e,ir_pg_vendors f where f.id=e.vendor_id and e.id=d.account_number "
							+ "and a.id=b.tran_id and a.Account_number=(select a.id from IR_VENDOR_APPS a,IR_PG_VENDORS b where "
							+ "a.VENDOR_ID=b.id and b.CODE=?) and a.STATUS=c.INT_CODE and c.TYPE_NAME='PAYMENT_STATUS' "
							+ " and a.status='5' and d.id=a.PAYMENT_OPTION_ID AND a.TXN_TYPE IN (0,3) and "
							+ " trunc(a.CREATED_ON) between (to_date(?,'DD-MM-YYYY')) AND (to_date(?,'DD-MM-YYYY')) order by a.ref_tran_id desc";
					final Object[] obj = new Object[3];
					obj[0] = vendorCode;
					obj[1] = fromDate;
					obj[2] = toDate;
					System.out.println("getVendorTransactionTextReport == "
							+ sql);
					result = jdbcTemplate.queryForList(sql, obj);
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	private boolean hasMinBalanceForVendor(long vendorAppId, long amount) {
		final String sql = "select min_amount from ir_vendor_apps where id=?";
		final Long minAmt = jdbcTemplate.queryForObject(sql,
				new Object[] { vendorAppId }, Long.class);
		if (minAmt >= amount) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void insertAddFundSmsData(String vendorCode, String amount) {
		final Map<String, Object> map = getvendordetails(vendorCode);

		String amt = "";
		String strDate = "";
		String mobno = "";
		try {
			final SimpleDateFormat sdfDate = new SimpleDateFormat("dd-MMM-yy");
			final Date now = new Date();
			strDate = sdfDate.format(now);

			amt = String.valueOf(map.get("AMOUNT"));
			mobno = (String) map.get("MOBILE_NO");

			final String type = "Balance Deposit SMS";
			final String desc = "This is to inform you that an amount of Rs."
					+ amount
					+ " has been credited in your RDS account. Available Bal is Rs."
					+ amt + " as on date " + strDate + "";
			final String sql = "insert into flight_system_info_sms values (flight_system_info_sms_seq.NEXTVAL,'"
					+ mobno
					+ "','"
					+ desc
					+ "','"
					+ strDate
					+ "','"
					+ type
					+ "')";
			jdbcTemplate.update(sql);

		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void insertAddVendorSmsData(IrPgVendor vendor) {
		String strDate = "";
		try {
			final SimpleDateFormat sdfDate = new SimpleDateFormat("dd-MMM-yy");
			final Date now = new Date();
			strDate = sdfDate.format(now);

			final String type = "New Deposit SMS";
			final String desc = "";

			jdbcTemplate
			.update("insert into flight_system_info_sms values (flight_system_info_sms_seq.NEXTVAL,"
					+ vendor.getVendorDetails().getVendorMobileNo()
					+ "," + desc + "," + strDate + "," + type + ")");
		} catch (final Exception e) {

		}
	}
}
