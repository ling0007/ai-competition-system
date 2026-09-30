package com.eliza.aicompetition.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页响应 DTO。
 * <p>
 * 所有分页接口返回此结构，前端可根据 total/pages 渲染完整分页控件。
 * 通过 {@link #of(IPage)} 静态工厂方法从 MyBatis-Plus Page 对象构建。
 * </p>
 *
 * @param <T> 当前页数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /** 当前页数据 */
    private List<T> records;
    /** 总记录数 */
    private long total;
    /** 总页数 */
    private long pages;
    /** 当前页码 */
    private long current;
    /** 每页大小 */
    private long size;

    /**
     * 从 MyBatis-Plus IPage 创建 PageResult。
     *
     * @param mpPage  MyBatis-Plus 分页结果对象
     * @param records 转换后的当前页数据
     * @param <T>     数据类型
     * @return 包含完整分页元数据的 PageResult
     */
    public static <T> PageResult<T> of(IPage<?> mpPage, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(mpPage.getTotal());
        result.setPages(mpPage.getPages());
        result.setCurrent(mpPage.getCurrent());
        result.setSize(mpPage.getSize());
        return result;
    }
}
