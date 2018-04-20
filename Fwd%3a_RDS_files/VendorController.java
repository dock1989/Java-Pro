package com.irctc.admin.web.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.irctc.admin.domain.request.DepositSummary;
import com.irctc.admin.domain.request.IrPgVendor;
import com.irctc.admin.domain.util.AdminUtil;
import com.irctc.admin.domain.util.EmailBodyUtil;
import com.irctc.admin.domain.util.JSONResponse;
import com.irctc.admin.domain.util.JSONUtil;
import com.irctc.admin.domain.util.MessageConstants;
import com.irctc.admin.persistence.dao.IrAirReconFileUploadDAO;
import com.irctc.admin.persistence.dao.VendorDAO;

@RestController
@RequestMapping("/Admin/vendor/")
@CrossOrigin
public class VendorController extends BaseController{
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	@Autowired
	private JSONResponse jsonResponse;
	
	@Autowired
	private IrAirReconFileUploadDAO fileUploadDao;
	
	@Autowired
	private VendorDAO vendorDao;
	
	
	@RequestMapping(value="/uploadFilePG", method=RequestMethod.POST )
	public JSONResponse uploadFilePGRecon(@RequestParam MultipartFile file,@RequestParam String fileDate, @RequestParam String pgName) {
		String resp	=	fileUploadDao.uploadFilePGVendor(file,fileDate,pgName);
		if(null != resp){
			if(resp.equals(MessageConstants.success)){
				return JSONUtil.setJSONResonse("", true,MessageConstants.FILE_UPLOAD_SUCCESS );
			}
			return JSONUtil.setJSONResonse("", true,MessageConstants.FILE_UPLOAD_FAILURE );
		}
		return jsonResponse;
		
	}
	
	@RequestMapping(value="/getVendorTransactionReport",method=RequestMethod.POST)
	public JSONResponse getVendorTransactionReport(@RequestBody VendorTransReport report){
		List<Map<String,Object>> response	=	vendorDao.getVendorTransactionReport(report.vendorCode,report.fromDate,report.toDate);
		if(null != response && !response.isEmpty())
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	@RequestMapping(value="/getVendorTransactionTextReport",method=RequestMethod.POST)
	public JSONResponse getVendorTransactionTextReport(@RequestBody VendorTransReport report){
		List<Map<String,Object>> response	=	vendorDao.getVendorTransactionTextReport(report.vendorCode,report.fromDate,report.toDate);
		if(null != response && !response.isEmpty())
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	@RequestMapping(value="/getVendorTransactionDetailReport",method=RequestMethod.POST)
	public JSONResponse getVendorTransactionDetailReport(@RequestBody VendorTransReport report){
		List<Map<String,Object>> response	=	vendorDao.getVendorTransactionDetailReport(report.vendorCode,report.fromDate,report.toDate,report.txnId);
		if(null != response && !response.isEmpty())
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	@RequestMapping(value="/getVendorRDSReport",method=RequestMethod.POST)
	public JSONResponse getVendorRDSReport(@RequestBody VendorTransReport report){
		List<Map<String,Object>> response	=	vendorDao.getVendorRDSReport(report.vendorCode,report.fromDate,report.toDate);
		if(null != response && !response.isEmpty())
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	@RequestMapping(value="/getVendorCancellationReport",method=RequestMethod.POST)
	public JSONResponse getVendorCancellationReport(@RequestBody VendorTransReport report){
		List<Map<String,Object>> response	=	vendorDao.getVendorCancellationReport(report.vendorCode,report.fromDate,report.toDate);
		if(null != response && !response.isEmpty())
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	@RequestMapping(value="/getVendorCancellationTextReport",method=RequestMethod.POST)
	public JSONResponse getVendorCancellationTextReport(@RequestBody VendorTransReport report){
		List<Map<String,Object>> response	=	vendorDao.getVendorCancellationTextReport(report.vendorCode,report.fromDate,report.toDate);
		if(null != response && !response.isEmpty())
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	private static class VendorTransReport { 
        public String vendorCode; 
        public String fromDate;
        public String toDate;
        public String txnId;
    }
	@RequestMapping(value="/add",method=RequestMethod.POST)
	public JSONResponse addVendor(@RequestBody IrPgVendor vendor){
		/* http://localhost:18078/Admin/vendor/add
		 {
		    
		    "code": "citrus",
		    "name": "citrus",
		    "password": "12345",
		    "username": "citrus",
		    "vendorDetails": {
		      
		      "vendorCode": "citrus",
		      "vendorTin": "12345",
		      "vendorGstn": "12345",
		      "dateOfIncorporation": "2010-10-20",
		      "vendorMobileNo": "9999999999",
		      "vendorEmailId": "citrus@citrus.com",
		      "vendorAddress": "delhi",
		      "vendorWebsiteUrl": "citrus.com"
		    }
		  }*/
		logger.info("VendorController || addVendor || Params :"+vendor.toString());
		String response	=	vendorDao.addVendor(vendor);
		if(null != response && !response.isEmpty() && response.contains(MessageConstants.success)){
			logger.info("VendorController || addVendor || response : "+response);
			
			//add vendor mail 
			//EmailBodyUtil.sendRegistrationRdsMail(vendor);
			
			//send sms
			//vendorDao.insertAddVendorSmsData(vendor);
			
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		}
		logger.warn("VendorController || addVendor || response : "+response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	@RequestMapping(value="/edit",method=RequestMethod.POST)
	public JSONResponse editVendor(@RequestBody IrPgVendor vendor){
		/*{
		    "id": 550,
		    "code": "paytm",
		    "name": "paytm",
		    "password": "12345",
		    "username": "paytm",
		    "status": false,
		    "vendorDetails": {
		      "id": 450,
		      "vendorTin": "98765",
		      "vendorGstn": "36987412",
		      "dateOfIncorporation": "2010-10-20",
		      "vendorMobileNo": "9999999999",
		      "vendorEmailId": "paytm@paytm.com",
		      "vendorAddress": "delhi",
		      "vendorWebsiteUrl": "paytm.com",
		      "createdBy": 123,
		      "createdOn": "2017-07-25",
		      "lastUpdatedBy": 0,
		      "lastUpdatedOn": null,
		      "serverIp": "10.34.42.35"
		    }
		  }*/
		logger.info("VendorController || editVendor || Params :"+vendor.toString());
		String response	=	vendorDao.editVendor(vendor);
		if(null != response && !response.isEmpty() && response.contains(MessageConstants.success)){
			logger.info("VendorController || editVendor || response : "+response);
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		}
		logger.warn("VendorController || editVendor || response : "+response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	@RequestMapping(value="/changestatus",method=RequestMethod.POST)
	public JSONResponse changeStatus(@RequestParam String code,@RequestParam boolean status){
/*http://localhost:18078/Admin/vendor/changestatus?code=paytm&status=true*/		
		logger.info("VendorController || changeStatus || Params :"+code+" && "+status);
		String response	=	vendorDao.changeStatus(code, status);
		if(null != response && !response.isEmpty() && response.contains(MessageConstants.success)){
			logger.info("VendorController || changeStatus || response : "+response);
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		}
		logger.warn("VendorController || changeStatus || response : "+response);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);
	}
	
	@RequestMapping(value="/vendordetails",method=RequestMethod.POST)
	public JSONResponse getVendorDetails(@RequestParam String code){
		/*http://localhost:18078/Admin/vendor/vendordetails?code=paytm*/
		logger.info("VendorController || getVendorDetails || Params :"+code);
		IrPgVendor obj	=	vendorDao.findVendorByCode(code);
		if(null != obj){
			logger.info("VendorController || getVendorDetails || response : "+obj);
			return JSONUtil.setJSONResonse(MessageConstants.success,true,obj);
		}
		logger.info("VendorController || getVendorDetails || response : "+MessageConstants.VENDOR_CODE_NOT_FOUND);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,MessageConstants.VENDOR_CODE_NOT_FOUND);
	}
	
	@SuppressWarnings("rawtypes")
	@RequestMapping(value="/allvendor",method=RequestMethod.GET)
	public JSONResponse getAllVendorDetails(){
		/*http://localhost:18078/Admin/vendor/allvendor*/
		logger.info("VendorController || getAllVendorDetails ");
		java.util.LinkedHashMap lhm	=	null;
		Integer roleId	=	null;
		Set roles	=	AdminUtil.getUserRolesFromJWTToken(request.getHeader(MessageConstants.JWT_HEADER).substring(7));
		for(Object o:roles){
			lhm = (java.util.LinkedHashMap)o;
			roleId	=	(Integer)lhm.get("roleId");
		}
		if(roleId==MessageConstants.PG_ROLE_ID){
			System.out.println("inside roleId==MessageConstants.PG_ROLE_ID");
			Iterable<IrPgVendor> obj	=	vendorDao.findVendorByCodeForPGList(AdminUtil.getUserNameFromJWTToken(request.getHeader(MessageConstants.JWT_HEADER).substring(7)).toUpperCase());
			if(null != obj){
				logger.info("VendorController || getAllVendorDetails || response : "+obj);
				return JSONUtil.setJSONResonse(MessageConstants.success,true,obj);
			}
			logger.warn("VendorController || getAllVendorDetails || response : "+MessageConstants.failure);
			return JSONUtil.setJSONResonse(MessageConstants.failure,false,MessageConstants.failure);
		}
		Iterable<IrPgVendor> obj	=	vendorDao.getAllVendors();
		if(null != obj){
			logger.info("VendorController || getAllVendorDetails || response : "+obj);
//				IrPgVendor select	=	new IrPgVendor();
//				select.setId(0);
//				select.setName(MessageConstants.ALL);
//				select.setCode(MessageConstants.EMPTY_STRING);
//				select.setVendorDetails(new IrPgVendorDetails());
//				ArrayList<IrPgVendor>	result	=	new ArrayList<IrPgVendor>();
//				result.add(select);
//				for(IrPgVendor p:obj){
//					result.add(p);
//				}
			
			return JSONUtil.setJSONResonse(MessageConstants.success,true,obj);
		}
		logger.warn("VendorController || getAllVendorDetails || response : "+MessageConstants.failure);
		return JSONUtil.setJSONResonse(MessageConstants.failure,false,MessageConstants.failure);
		
	}
	@RequestMapping(value="/addfunds",method=RequestMethod.POST)
	public JSONResponse addFunds(@RequestParam String vendorCode,@RequestParam String amount,@RequestParam String bankName,
			@RequestParam String refNumber,@RequestParam String dateOfDD){
		//,@RequestParam String remarks removed on 30112017
		/*http://localhost:18023/Admin/vendor/addfunds?appCode=AIR_TKT&amount=20000&payMode=cheque&refNumber=36456&dateOfApproval=2017/03/21&vendorCode=paytm*/
	
		logger.info("VendorController || addfunds || Params :"+vendorCode+"|"+bankName+"|"+refNumber+"|"+dateOfDD);
		
		String response	=vendorDao.addFundsToVendorAccount(vendorCode,amount,bankName,refNumber,dateOfDD);
	
		if(null != response && !response.isEmpty() && response.contains(MessageConstants.success)){
			try{
				//send mail 
				EmailBodyUtil.sendAmountDepositMail(vendorDao.getvendordetails(vendorCode),amount);
			
				//send sms
				vendorDao.insertAddFundSmsData(vendorCode,amount);
			}catch(Exception e){
				e.printStackTrace();
			}
			return JSONUtil.setJSONResonse(MessageConstants.success,true,response);
		}
		else{return JSONUtil.setJSONResonse(MessageConstants.failure,false,response);}
	}
	
	@RequestMapping(value="/getDepositSummary",method=RequestMethod.POST)
	public JSONResponse getDepositSummary(@RequestBody DepositSummary ds){
		
		List<Map<String, Object>> list = vendorDao.getDepositSummary(ds);
		if(null != list && !list.isEmpty()){
			for (Map<String, Object> map : list) {
				if(map.get("DESCRIPTION")!=null &&
						!map.get("DESCRIPTION").toString().isEmpty()){
				 String[] splitDesc = map.get("DESCRIPTION").toString().split(":");
				 if(splitDesc!=null && splitDesc.length>2){
					 map.put("BANK", splitDesc[0]);
					 map.put("DD_NUM", splitDesc[1]);
					 map.put("DD_DATE", splitDesc[2]);
					 map.remove("DESCRIPTION");
				 }
				}
			}
			return JSONUtil.setJSONResonse(MessageConstants.success,true,list);
		}
		else{return JSONUtil.setJSONResonse(MessageConstants.failure,false,false);}
}
	
	

}
