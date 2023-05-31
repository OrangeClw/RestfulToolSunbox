package com.github.restful.tool.utils.scanner;

import com.github.restful.tool.beans.HttpMethod;
import com.github.restful.tool.beans.Request;
import com.github.restful.tool.utils.ProjectConfigUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class SunboxHelper {

    @NotNull
    public static List<Request> getSunboxRequestByModule(@NotNull Project project, @NotNull Module module) {
        List<Request> moduleList = new ArrayList<>(0);

        List<PsiClass> controllers = getAllActionClass(project, module);
        if (controllers.isEmpty()) {
            return moduleList;
        }

        for (PsiClass controllerClass : controllers) {
            moduleList.addAll(getRequests(controllerClass));
        }

        return moduleList;
    }


    @NotNull
    public static List<Request> getRequests(@NotNull PsiClass psiClass) {
        // 初始空请求列表
        List<Request> requests = new ArrayList<>();

        // 获取包含此类的文件，如果不是Java文件，则直接返回空请求列表
        PsiFile containingFile = psiClass.getContainingFile();
        if (!(containingFile instanceof PsiJavaFile)) {
            return requests;
        }

        // 从包名中获取最后一部分作为路径的一部分
        String[] packageNameComponents = ((PsiJavaFile) containingFile).getPackageName().split("\\.");
        String lastPackageName = packageNameComponents[packageNameComponents.length - 1];

        // 获取类名，如果为空，则直接返回空请求列表
        String className = psiClass.getName();
        if (className == null) {
            return requests;
        }

        // 将类名格式化为路径格式（例如将驼峰格式转换为下划线格式）
        String formattedClassName = className.replaceAll("(?<=.)(\\p{Upper})", "_$1").toLowerCase();
        if (formattedClassName.contains("_action")) {
            formattedClassName = formattedClassName.substring(0, formattedClassName.lastIndexOf("_action"));
        }

        // 默认路径前缀为 "rest"
        String pathPrefix = "rest";

        // 检查类的超类，以决定路径前缀是 "json" 还是 "rest"
        PsiClass superClass = psiClass.getSuperClass();
        String superClassName = "";
        if (superClass != null) {
            superClassName = superClass.getQualifiedName();
            if ("sunbox.core.action.app.AppAction".equals(superClassName)
                    || "sunbox.core.action.system.SystemAction".equals(superClassName)) {
                pathPrefix = "json";
            }
        }


        // 获取并处理类的所有方法
        PsiMethod[] psiMethods = psiClass.getAllMethods();
        for (PsiMethod psiMethod : psiMethods) {
            // 获取并处理方法的所有注解
            PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();
            for (PsiAnnotation annotation : annotations) {
                // 判断是否有指定的注解
                if (annotation.getQualifiedName() != null && annotation.getQualifiedName().equals("sunbox.core.action.ActionConstructInit")) {
                    // 默认HTTP方法为SUNBOX，你可以根据需要更改
                    HttpMethod httpMethod = HttpMethod.SUNBOX;

                    // 构造默认路径
                    String path = "/" + lastPackageName + "/" + pathPrefix + "/" + formattedClassName + "/" + psiMethod.getName();

                    // 获取方法的返回类型
                    PsiType returnType = psiMethod.getReturnType();
                    if (returnType != null) {
                        if (returnType instanceof PsiClassType) {
                            PsiClassType classType = (PsiClassType) returnType;
                            PsiClass resolvedClass = classType.resolve();
                            // 如果返回类型为 "java.util.List"，则将路径前缀设置为 "jqGrid"
                            if (resolvedClass != null && "java.util.List".equals(resolvedClass.getQualifiedName())
                                    && "sunbox.core.action.app.AppAction".equals(superClassName)) {
                                path = "/" + lastPackageName + "/jqGrid/" + formattedClassName + "/" + psiMethod.getName();
                            }
                        }
                        // 如果返回值内容为 "sunbox.core.action.BaseAction#EXCEL"，则将路径前缀设置为 "excel"
                        else if ("sunbox.core.action.BaseAction#EXCEL".equals(returnType.getCanonicalText())) {
                            path = "/" + lastPackageName + "/excel/" + formattedClassName + "/" + psiMethod.getName();
                        }
                    }

                    // 创建请求并添加到请求列表中
                    Request request = new Request(httpMethod, path, psiMethod);
                    requests.add(request);
                    break;
                }
            }
        }

        // 返回请求列表
        return requests;
    }



    @NotNull
    private static List<PsiClass> getAllActionClass(@NotNull Project project, @NotNull Module module) {
        List<PsiClass> allActionClasses = new ArrayList<>();

        // 获取模块的搜索范围
        GlobalSearchScope moduleScope = ProjectConfigUtil.getModuleScope(module);

        // 从项目中找到BaseAction类
        PsiClass baseActionClass = JavaPsiFacade.getInstance(project).findClass("sunbox.core.action.BaseAction", GlobalSearchScope.allScope(project));

        // 如果没有找到BaseAction类，就直接返回空列表
        if (baseActionClass == null) {
            return allActionClasses;
        }

        // 获取整个项目中的所有类
        Collection<VirtualFile> virtualFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, moduleScope);
        for (VirtualFile virtualFile : virtualFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile instanceof PsiJavaFile) {
                PsiClass[] psiClasses = ((PsiJavaFile) psiFile).getClasses();
                for (PsiClass psiClass : psiClasses) {
                    // 如果类是BaseAction的子类或实现类，则添加到结果列表中
                    if (psiClass.isInheritor(baseActionClass, true)) {
                        allActionClasses.add(psiClass);
                    }
                }
            }
        }

        return allActionClasses;
    }



}
