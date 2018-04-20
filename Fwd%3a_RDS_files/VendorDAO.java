package com.irctc.admin.persistence.dao;

import java.util.List;
import java.util.Map;

import com.irctc.admin.domain.request.DepositSummary;
import com.irctc.admin.domain.request.IrPgVendor;

public interface VendorDAO {
	public String addVendor(IrPgVendor vendor);
	public String editVendor(IrPgVendor vendor);
	public IrPgVendor findVendorByCode(String code);
	public String changeStatus(String code,boolean status);
	public Iterable<IrPgVendor> getAllVendors();
	public String addFundsToVendorAccount(String vendorCode, String amount,
			String bankName, String refNumber, String dateOfApproval);
	public List<Map<String, Object>> getVendorTransactionReport(
			String vendorCode, String fromDate, String toDate);
	public List<Map<String, Object>> getVendorRDSReport(String vendorCode,
			String fromDate, String toDate);
	public List<Map<String, Object>> getVendorCancellationReport(
			String vendorCode, String fromDate, String toDate);
	public List<Map<String, Object>> getDepositSummary(DepositSummary ds);
	public List<Map<String, Object>> getVendorTransactionDetailReport(
			String vendorCode, String fromDate, String toDate,String txnId);
	Iterable<IrPgVendor> findVendorByCodeForPGList(String code);
	public Map<String, Object> getvendordetails(String vendorCode);
	public void insertAddFundSmsData(String vendorCode,String amount);
	public void insertAddVendorSmsData(IrPgVendor vendor);
	public List<Map<String, Object>> getVendorCancellationTextReport(
			String vendorCode, String fromDate, String toDate);
	public List<Map<String, Object>> getVendorTransactionTextReport(
			String vendorCode, String fromDate, String toDate);
	
}
