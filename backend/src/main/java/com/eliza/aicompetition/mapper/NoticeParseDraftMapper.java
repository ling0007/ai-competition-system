package com.eliza.aicompetition.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eliza.aicompetition.entity.NoticeParseDraft;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 解析草稿 Mapper。
 */
@Mapper
public interface NoticeParseDraftMapper extends BaseMapper<NoticeParseDraft> {

    /**
     * 查找指定通知的最新 PENDING 草稿。
     *
     * @param noticeId 通知 ID
     * @return 最新草稿，无则返回 null
     */
    default NoticeParseDraft findLatestPendingByNoticeId(Long noticeId) {
        LambdaQueryWrapper<NoticeParseDraft> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoticeParseDraft::getNoticeId, noticeId)
            .eq(NoticeParseDraft::getStatus, "PENDING")
            .orderByDesc(NoticeParseDraft::getCreatedAt)
            .last("LIMIT 1");
        return selectOne(queryWrapper);
    }

    /**
     * 查找指定通知的最新已确认草稿。
     *
     * @param noticeId 通知 ID
     * @return 最新已确认草稿，无则返回 null
     */
    default NoticeParseDraft findLatestConfirmedByNoticeId(Long noticeId) {
        LambdaQueryWrapper<NoticeParseDraft> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoticeParseDraft::getNoticeId, noticeId)
            .eq(NoticeParseDraft::getStatus, "CONFIRMED")
            .orderByDesc(NoticeParseDraft::getCreatedAt)
            .last("LIMIT 1");
        return selectOne(queryWrapper);
    }
}
