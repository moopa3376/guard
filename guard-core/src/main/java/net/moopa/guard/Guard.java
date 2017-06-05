package net.moopa.guard;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.moopa.guard.checker.PermissionChecker;
import net.moopa.guard.checker.SignInChecker;
import net.moopa.guard.config.GuardConfigs;
import net.moopa.guard.jwt.JwtWrapper;
import net.moopa.guard.model.account.Account;
import net.moopa.guard.model.permission.Permission;
import net.moopa.guard.model.role.Role;
import net.moopa.guard.service.IGuardService;
import net.moopa.guard.token.AuthorizeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Created by Moopa on 13/05/2017.
 * blog: leeautumn.net
 *
 * 该类主要就只是一个门户,将一些操作统一集成到这个类中,主要操作有:
 * 1.登录
 * 2.下线
 * 3.加密
 * 4.解密
 *
 * 还有一些全局的变量也被存放在这个类中
 * 全局的服务guardService也放在这个类中
 * @autuor : Moopa
 */
public class Guard {
    private static Logger logger = LoggerFactory.getLogger(Guard.class);

    public static boolean isInited = false;
    public static IGuardService guardService = null;
    //缓存
    private static GuardCache cache = new GuardCache();
    protected static SignInChecker signInChecker = new SignInChecker();
    protected static PermissionChecker permissionChecker = new PermissionChecker();




    /**
     * 该方法用来进行登录操作,登录成功后返回
     * 交给SignInChecker来进行登录
     * @return null-登录不成功
     */
    public static AuthorizeToken signin(String loginname,String password,ServletResponse servletResponse){
        AuthorizeToken authorizeToken = signInChecker.signin(loginname,password);

        if(authorizeToken != null) {
            //往http header中添加X-token
            ((HttpServletResponse) servletResponse).setHeader("X-token", authorizeToken.getJwtToken());
            //往cache中添加相关项
            cache.tokenCache.put(authorizeToken.getJwtToken(),authorizeToken);
        }else {
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        return authorizeToken;
    }

    public static boolean isTokenExisted(String tokenname){
        return cache.tokenCache.containsKey(tokenname);
    }

    /**
     * 该方法主要用于用户的下线操作
     * 根据token来进行下线
     */
    public static boolean signoff(String tokenname){
        //直接在缓存中删除相应authorizeToken
        cache.tokenCache.remove(tokenname);
        //不删除account缓存

        return true;
    }


    public static boolean permissionCheck(String tokenname,String permission){
        Account account = getAccountByLoginname(cache.getAuthorizeTokenByTokenName(tokenname).getLoginname());

        Role role = cache.getRolenameByRoleId(account.getRole_id());

        return permissionChecker.permissionCheck(role,permission);
    }

    public static Account getAccount(ServletRequest servletRequest){
        String tokenname = ((HttpServletRequest)servletRequest).getHeader("X-token");
        if(tokenname == null){
            return null;
        }
        return getAccountByLoginname(cache.getAuthorizeTokenByTokenName(tokenname).getLoginname());
    }


    //token相关操作
    public static AuthorizeToken getAuthorizeToken(ServletRequest servletRequest){
        String tokenname = ((HttpServletRequest)servletRequest).getHeader("X-token");
        if(tokenname == null){
            return null;
        }
        AuthorizeToken token = getAuthorizeTokenByName(tokenname);
        if(token == null){
            token = new AuthorizeToken();
            token.updateJwtToken(tokenname);
            return token;
        }
        return token;
    }

    public static AuthorizeToken updateAuthorizeToken(AuthorizeToken token,String key,String val,HttpServletResponse httpServletResponse){
        String jwt = token.getJwtToken();
        DecodedJWT decodedJWT = JwtWrapper.verifyAndDecodeJwt(jwt);
        String payload = decodedJWT.getPayload();

        //进行json解析
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = (JsonObject) jsonParser.parse(payload);
        jsonObject.addProperty(key,val);

        String res_token = JwtWrapper.getJwt(jsonObject);
        token.updateJwtToken(res_token);

        //接下来在http-header中进行更新
        httpServletResponse.setHeader("X-token",res_token);

        return token;
    }

    public static String getValFromToken(AuthorizeToken token,String key){
        String jwt = token.getJwtToken();
        //直接认为是已知正确的token
        //获取payload
        String payload = jwt.split("\\.")[1];
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(payload);
        return jsonObject.get(key).getAsString();
    }

    //缓存相关操作
    public static Account getAccountByLoginname(String loginname){
        Account account = cache.getAccount(loginname);
        if(account == null){
            account = guardService.getAccountByLoginname(loginname);
            if(account == null){
                logger.error("can't get account by loginname {}, please ensure your guard service class implement correctly.",loginname);
                return null;
            }
            cache.putAccount(loginname,account);
        }
        return account;
    }

    public static AuthorizeToken getAuthorizeTokenByName(String tokenname){
        AuthorizeToken authorizeToken = cache.getAuthorizeTokenByTokenName(tokenname);
        return authorizeToken;
    }


    private static void init(){
        if(!isInited){


            //初始化缓存,读入角色和权限数据
            cache = new GuardCache();
            List<Role> roles = guardService.leadIntoRole();
            List<Permission> permissions = guardService.leadIntoPermission();

            //匹配角色和权限
            guardService.matchRoleAndPermission(roles,permissions);

            //加入缓存
            for(Role r : roles){
                cache.roleCache.put(r.getRolename(),r);
                //匹配roleid和rolename
                cache.roleIdMapRolename.put(r.getRole_id(),r);
            }

            //初始化结束
            isInited = true;
        }
    }

    static{
        //获取用户自身所定义的服务实现类
        String serviceClass = GuardConfigs.get("guard.guardService");
        Class servClass = null;
        try {
            servClass = Class.forName(serviceClass.trim());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            guardService = (IGuardService) (servClass.newInstance());
        } catch (InstantiationException e) {
            System.err.println("Please add the constructor with no parameter.\n");
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            System.err.println("Please add the public constructor with no parameter.\n");
            e.printStackTrace();
        }

        //进行初始化
        init();
    }
}
