package com.xuecheng.ucenter.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.mapper.XcUserRoleMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.model.po.XcUserRole;
import com.xuecheng.ucenter.service.AuthService;
import com.xuecheng.ucenter.service.WxAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 微信认证服务实现类
 *
 * 1. 对接微信 OAuth2 授权码登录流程
 * 2. 使用授权码换取 access_token 和 openid
 * 3. 获取微信用户基本信息
 * 4. 自动注册/登录本地用户
 *
 * 同时实现了 AuthService（统一认证接口）和 WxAuthService（微信认证接口）
 */
@Slf4j
@Service("wx_authservice")
public class WxAuthServiceImpl implements AuthService, WxAuthService {
    @Value("${weixin.appid:}")
    private String appid;

    @Value("${weixin.secret:}")
    private String secret;


    @Autowired
    RestTemplate restTemplate;

    @Autowired
    XcUserRoleMapper xcUserRoleMapper;

    @Autowired
    XcUserMapper xcUserMapper;

    @Autowired
    WxAuthServiceImpl currentProxy;

    /**
     * 统一认证入口 —— 根据用户名查询本地用户
     * 此处为 AuthService 接口的实现，用于密码模式等非微信登录场景
     */
    @Override
    public XcUserExt execute(AuthParamsDto authParamsDto) {

        // 根据用户名查询用户信息
        String username = authParamsDto.getUsername();
        XcUser user = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getUsername, username));
        if(user==null){
            // 用户不存在则抛出异常，由全局异常处理器返回错误响应
            throw new RuntimeException("账号不存在");
        }
        XcUserExt xcUserExt = new XcUserExt();
        BeanUtils.copyProperties(user,xcUserExt);
        return xcUserExt;
    }

    /**
     * 微信授权码登录主流程
     *
     * @param code 微信 OAuth2 授权码（前端从微信回调中获取）
     * @return 登录成功的本地用户信息
     */
    @Override
    public XcUser wxAuth(String code){

        // step1: 用授权码向微信服务器申请 access_token
        Map<String, String> access_token_map = getAccess_token(code);
        if(access_token_map==null){
            return null;
        }
        System.out.println(access_token_map);
        String openid = access_token_map.get("openid");
        String access_token = access_token_map.get("access_token");
        // step2: 用 access_token 获取微信用户基本信息（昵称、头像等）
        Map<String, String> userinfo = getUserinfo(access_token, openid);
        if(userinfo==null){
            return null;
        }
        // step3: 将微信用户信息写入本地数据库（首次登录自动注册，已存在则直接返回）
        XcUser xcUser = currentProxy.addWxUser(userinfo);

        return xcUser;
    }

    /**
     * 向微信服务器申请访问令牌（access_token）
     *
     * 响应示例：
     * {
     * "access_token":"ACCESS_TOKEN",
     * "expires_in":7200,
     * "refresh_token":"REFRESH_TOKEN",
     * "openid":"OPENID",
     * "scope":"SCOPE",
     * "unionid": "o6_bmasdasdsad6_2sgVt7hMZOPfL"
     * }
     *
     * @param code 微信授权码
     * @return 包含 access_token、openid、unionid 等的 map
     */
    //todo 需要在nacos中配置weixin appid and secret
    private Map<String, String> getAccess_token(String code) {
        // 组装请求微信接口的 URL
        String wxUrl_template = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
        String wxUrl = String.format(wxUrl_template, appid, secret, code);
        log.info("调用微信接口申请access_token, url:{}", wxUrl);
        // 发送 POST 请求获取 access_token
        ResponseEntity<String> exchange = restTemplate.exchange(wxUrl, HttpMethod.POST, null, String.class);

        String result = exchange.getBody();
        log.info("调用微信接口申请access_token: 返回值:{}", result);
        Map<String, String> resultMap = JSON.parseObject(result, Map.class);

        return resultMap;
    }

    /**
     * 获取微信用户基本信息
     *
     * 响应示例：
     * {
     * "openid":"OPENID",
     * "nickname":"NICKNAME",
     * "sex":1,
     * "province":"PROVINCE",
     * "city":"CITY",
     * "country":"COUNTRY",
     * "headimgurl": "https://thirdwx.qlogo.cn/mmopen/...",
     * "privilege":["PRIVILEGE1","PRIVILEGE2"],
     * "unionid": "o6_bmasdasdsad6_2sgVt7hMZOPfL"
     * }
     *
     * @param access_token 微信访问令牌
     * @param openid 微信用户 openid
     * @return 包含用户昵称、头像等信息的 map
     */
    private Map<String, String> getUserinfo(String access_token, String openid) {
        // 组装请求微信用户信息接口的 URL
        String wxUrl_template = "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s";
        String wxUrl = String.format(wxUrl_template, access_token, openid);

        log.info("调用微信接口申请access_token, url:{}", wxUrl);
        // 发送 POST 请求获取用户信息
        ResponseEntity<String> exchange = restTemplate.exchange(wxUrl, HttpMethod.POST, null, String.class);

        // 防止中文乱码，将 ISO-8859-1 编码转成 UTF-8
        String result = new String(exchange.getBody().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        log.info("调用微信接口申请access_token: 返回值:{}", result);
        Map<String, String> resultMap = JSON.parseObject(result, Map.class);

        return resultMap;
    }

    /**
     * 添加微信用户到本地数据库（事务）
     * - 如果 unionid 已存在则直接返回已有用户（不重复注册）
     * - 否则创建新用户并分配默认学生角色
     *
     * @param userInfo_map 微信用户信息 map（包含 unionid、nickname、headimgurl 等）
     * @return 本地用户信息
     */
    @Transactional
    public XcUser addWxUser(Map userInfo_map){
        // 以 unionid 作为唯一标识，查询是否已注册
        String unionid = userInfo_map.get("unionid").toString();
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getWxUnionid, unionid));
        if(xcUser!=null){
            // 已注册则直接返回已有用户信息
            return xcUser;
        }
        // 首次登录，创建新用户并填充微信返回的信息
        String userId = UUID.randomUUID().toString();
        xcUser = new XcUser();
        xcUser.setId(userId);
        xcUser.setWxUnionid(unionid);
        xcUser.setNickname(userInfo_map.get("nickname").toString());
        xcUser.setUserpic(userInfo_map.get("headimgurl").toString());
        xcUser.setName(userInfo_map.get("nickname").toString());
        xcUser.setUsername(unionid);           // 以 unionid 作为用户名
        xcUser.setPassword(unionid);           // 默认密码设为 unionid（后续可引导用户修改）
        xcUser.setUtype("101001");             // 用户类型：学生
        xcUser.setStatus("1");                 // 用户状态：正常
        xcUser.setCreateTime(LocalDateTime.now());
        xcUserMapper.insert(xcUser);
        // 为用户分配默认的学生角色
        XcUserRole xcUserRole = new XcUserRole();
        xcUserRole.setId(UUID.randomUUID().toString());
        xcUserRole.setUserId(userId);
        xcUserRole.setRoleId("17");            // 角色 ID：学生
        xcUserRoleMapper.insert(xcUserRole);
        return xcUser;
    }
}
