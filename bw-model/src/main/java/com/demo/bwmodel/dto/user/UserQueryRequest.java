package com.demo.bwmodel.dto.user;



import com.demo.bwcommon.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    /**
     * id
     */
    private Long id;
    /**
     * 用户昵称
     */
    private String userAccount;

    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;

    /**
     * 是否使用模糊搜索模式（默认false为精确匹配，true为模糊匹配）
     */
    private Boolean fuzzySearch = false;

    private static final long serialVersionUID = 1L;
}
