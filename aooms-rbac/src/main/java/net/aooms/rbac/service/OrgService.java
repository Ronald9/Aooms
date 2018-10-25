package net.aooms.rbac.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import net.aooms.core.Aooms;
import net.aooms.core.AoomsConstants;
import net.aooms.core.id.IDGenerator;
import net.aooms.core.module.mybatis.Db;
import net.aooms.core.module.mybatis.Roper;
import net.aooms.core.module.mybatis.SqlExpression;
import net.aooms.core.module.mybatis.SqlPara;
import net.aooms.core.record.PagingRecord;
import net.aooms.core.record.Record;
import net.aooms.core.record.TreeRecord;
import net.aooms.core.service.GenericService;
import net.aooms.core.util.Kv;
import net.aooms.core.util.TreeUtils;
import net.aooms.rbac.mapper.RbacMapperPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 机构管理
 * Created by 风象南(cheereebo) on 2018-10-08
 */
@Service
public class OrgService extends GenericService {

    @Autowired
    private Db db;

    @Transactional(readOnly = true)
    public void findList() {
        SqlPara sqlPara = SqlPara.fromDataBoss().paging();
        sqlPara.and("status")
                .andLikeAfter("data_permission","org_name","org_shortname","org_code")
                .orGroup(new SqlExpression("parent_org_id",Roper.Eq), new SqlExpression("id",Roper.Eq,"parent_org_id"))
        ;

        String statementId = getStatementId(RbacMapperPackage.class,"OrgMapper.findList");
		PagingRecord pagingRecord = db.findList(statementId,sqlPara);
		this.setResultValue(AoomsConstants.Result.DATA,pagingRecord);
	}

	@Transactional(readOnly = true)
	public void findTree() {
		String statementId = getStatementId(RbacMapperPackage.class,"OrgMapper.findList");
		PagingRecord pagingRecord = db.findList(statementId,SqlPara.SINGLETON);

        TreeUtils treeUtils = new TreeUtils(pagingRecord.getList());
        treeUtils.setParentIdKey("parent_org_id");
        treeUtils.setDefaultValue(Kv.fkv("icon","el-icon-news"));
        List<Record> treeRecords = treeUtils.listTree(AoomsConstants.TREE_ROOT, true);

		this.setResultValue(AoomsConstants.Result.TREE, treeRecords);
	}

	@Transactional
	public void insert() {
		Record record = Record.empty();
		record.set(AoomsConstants.ID,IDGenerator.getStringValue());
		record.setByJsonKey("formData");
		record.set("create_time", DateUtil.now());
		String parentId = record.getString("parent_org_id");
		String orgPermission = this.permissionCode(parentId);
		record.set("org_permission", orgPermission);
        record.set("org_level", this.orgLevel(parentId) + 1);
        record.set("data_permission", this.dataPermission(parentId,orgPermission));
        db.insert("aooms_rbac_org",record);

		// 返回前台
		record.set("icon","el-icon-news");
		this.setResultValue(AoomsConstants.Result.RECORD, record);
	}

	@Transactional
	public void update() {
        Record record = Record.empty();
        record.setByJsonKey("formData");
        record.set("update_time",DateUtil.now());
        String parentId = record.getString("parent_org_id");
        String orgPermission = this.permissionCode(parentId);
        record.set("org_permission", orgPermission);
        record.set("org_level", this.orgLevel(record.getString("parent_org_id")) + 1);
        record.set("data_permission", this.dataPermission(parentId,orgPermission));
        db.update("aooms_rbac_org",record);

        record.set("icon","el-icon-news");
        this.setResultValue(AoomsConstants.Result.RECORD, record);
    }

    @Transactional
    public void updateStatus() {
        Record record = Record.empty();
        record.set(AoomsConstants.ID, getParaString("id"));
        record.set("status", getParaString("status"));
        record.set("update_time",DateUtil.now());
        db.update("aooms_rbac_org",record);
    }

	@Transactional
	public void delete() {
        List<Object> ids = this.getListFromJson("ids",AoomsConstants.ID);
		db.batchDelete("aooms_rbac_org",ids.toArray());
	}

    /**
     *
     * 私有辅助
     *
     */

    // 生成permissionCode
    private String permissionCode(String parentId){
        int permissionLen = 4;
        String permission;

        synchronized (this.getClass()){
            String statementId = getStatementId(RbacMapperPackage.class,"OrgMapper.findMaxOrgPermission");
            Integer maxPermission = db.findObject(statementId,SqlPara.empty().set("parent_org_id",parentId),Integer.class);
            if(maxPermission == null){
                maxPermission = 0;
            }
            maxPermission += 1;

            permission = maxPermission + "";
            if(permission.length() > permissionLen){
                throw new RuntimeException("The org_permission length is not allowed to be greater than " + permissionLen);
            }

            int start = permission.length();
            for(int index = start; index < permissionLen; index++){
                permission = "0" + permission;
            };
        }
        return permission;
    }

    // 获取orgLevel
    private Integer orgLevel(String parentId){
        String statementId = getStatementId(RbacMapperPackage.class,"OrgMapper.findOrgLevel");
        Integer orgLevel = db.findObject(statementId,SqlPara.empty().set("parent_org_id",parentId),Integer.class);
        if(orgLevel == null){
            orgLevel = 0;
        }
        return orgLevel;
    }

    // 生成data_permission
    private String dataPermission(String parentId,String orgPermission){
        char permissionSplicChar = '.';
        StringBuilder dataPermission = new StringBuilder(orgPermission);
        Record record = db.findByPrimaryKey("aooms_rbac_org", parentId);
        while(record != null){
            dataPermission.insert(0,record.getString("org_permission") + permissionSplicChar);
            record = db.findByPrimaryKey("aooms_rbac_org",record.getString("parent_org_id"));
        }
        return dataPermission.toString();
    }

}