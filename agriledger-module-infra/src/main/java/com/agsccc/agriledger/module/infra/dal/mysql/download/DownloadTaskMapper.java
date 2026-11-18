package com.agsccc.agriledger.module.infra.dal.mysql.download;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.agsccc.agriledger.module.infra.controller.admin.download.vo.download.DownloadTaskPageReqVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.download.DownloadTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.agsccc.agriledger.module.infra.enums.download.DownloadTaskStatusEnum.*;

/**
 * 下载中心任务 Mapper
 *
 * <p>终态转移一律走条件化更新（CAS）：以受影响行数判定是否获得转移权，
 * 消除「用户取消与 Worker 完成登记」并发竞态，并保证状态不倒退。
 *
 * @author Sail
 */
@Mapper
public interface DownloadTaskMapper extends BaseMapperX<DownloadTaskDO> {

    default PageResult<DownloadTaskDO> selectPageByCreator(String creator, DownloadTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DownloadTaskDO>()
                .eq(DownloadTaskDO::getCreator, creator)
                .eqIfPresent(DownloadTaskDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DownloadTaskDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DownloadTaskDO::getId));
    }

    default Long selectCountByCreatorAndStatus(String creator, Set<Integer> status) {
        return selectCount(new LambdaQueryWrapperX<DownloadTaskDO>()
                .eq(DownloadTaskDO::getCreator, creator)
                .in(DownloadTaskDO::getStatus, status));
    }

    /**
     * 统计创建者处于指定状态集合中的任务数（排队中 + 打包中均占用并发额度）
     */
    default Long selectCountByCreatorAndStatusIn(String creator, Collection<Integer> statuses) {
        return selectCount(new LambdaQueryWrapperX<DownloadTaskDO>()
                .eq(DownloadTaskDO::getCreator, creator)
                .in(DownloadTaskDO::getStatus, statuses));
    }

    /**
     * 按创建者与幂等标识查询任务（防重复提交时返回原任务）
     */
    default DownloadTaskDO selectByCreatorAndRequestId(String creator, String requestId) {
        return selectOne(new LambdaQueryWrapperX<DownloadTaskDO>()
                .eq(DownloadTaskDO::getCreator, creator)
                .eq(DownloadTaskDO::getRequestId, requestId)
                .orderByDesc(DownloadTaskDO::getId));
    }

    default List<DownloadTaskDO> selectListByStatusAndUpdateTimeBefore(Integer status, LocalDateTime updateTime) {
        return selectList(new LambdaQueryWrapperX<DownloadTaskDO>()
                .eq(DownloadTaskDO::getStatus, status)
                .lt(DownloadTaskDO::getUpdateTime, updateTime)
                .orderByAsc(DownloadTaskDO::getId));
    }

    default List<DownloadTaskDO> selectListByStatusAndExpiredTimeBefore(Integer status, LocalDateTime expiredTime) {
        return selectList(new LambdaQueryWrapperX<DownloadTaskDO>()
                .eq(DownloadTaskDO::getStatus, status)
                .lt(DownloadTaskDO::getExpiredTime, expiredTime)
                .orderByAsc(DownloadTaskDO::getId));
    }

    default List<DownloadTaskDO> selectListByStatusInAndUpdateTimeBefore(Collection<Integer> statuses, LocalDateTime updateTime) {
        return selectList(new LambdaQueryWrapperX<DownloadTaskDO>()
                .in(DownloadTaskDO::getStatus, statuses)
                .lt(DownloadTaskDO::getUpdateTime, updateTime)
                .orderByAsc(DownloadTaskDO::getId));
    }

    /**
     * 条件化状态转移：仅当当前状态为 fromStatus 时更新为 toStatus
     *
     * @return true 表示转移成功（当前调用方获得转移权）
     */
    default boolean updateStatusIf(Long id, Integer fromStatus, Integer toStatus) {
        return update(null, new LambdaUpdateWrapper<DownloadTaskDO>()
                .eq(DownloadTaskDO::getId, id)
                .eq(DownloadTaskDO::getStatus, fromStatus)
                .set(DownloadTaskDO::getStatus, toStatus)) > 0;
    }

    /**
     * 打包成功的条件化登记：仅当任务仍为打包中时生效，
     * 失败说明任务已被并发取消，调用方需回收已上传的产物
     */
    default boolean updateTaskSuccessIfProcessing(Long id, Long fileSize, Long storageConfigId, String storagePath,
                                                  String storageUrl, LocalDateTime expiredTime, LocalDateTime completedTime) {
        return update(null, new LambdaUpdateWrapper<DownloadTaskDO>()
                .eq(DownloadTaskDO::getId, id)
                .eq(DownloadTaskDO::getStatus, PROCESSING.getStatus())
                .set(DownloadTaskDO::getStatus, SUCCESS.getStatus())
                .set(DownloadTaskDO::getFileSize, fileSize)
                .set(DownloadTaskDO::getStorageConfigId, storageConfigId)
                .set(DownloadTaskDO::getStoragePath, storagePath)
                .set(DownloadTaskDO::getStorageUrl, storageUrl)
                .set(DownloadTaskDO::getExpiredTime, expiredTime)
                .set(DownloadTaskDO::getCompletedTime, completedTime)) > 0;
    }

    /**
     * 打包失败的条件化登记：仅当任务仍为打包中时生效（已被取消的任务不再改写为失败）
     */
    default boolean updateTaskFailedIfProcessing(Long id, String errorMessage) {
        return update(null, new LambdaUpdateWrapper<DownloadTaskDO>()
                .eq(DownloadTaskDO::getId, id)
                .eq(DownloadTaskDO::getStatus, PROCESSING.getStatus())
                .set(DownloadTaskDO::getStatus, FAILED.getStatus())
                .set(DownloadTaskDO::getErrorMessage, errorMessage)) > 0;
    }

    /**
     * 消费者崩溃恢复的重排队：仅当任务仍为打包中时回到排队态并累加重试计数，
     * 由调用方随后重发消息（重复投递由消费端 CAS 抢占幂等吸收）
     */
    default boolean updateTaskRequeueIfProcessing(Long id, int newRetryCount) {
        return update(null, new LambdaUpdateWrapper<DownloadTaskDO>()
                .eq(DownloadTaskDO::getId, id)
                .eq(DownloadTaskDO::getStatus, PROCESSING.getStatus())
                .set(DownloadTaskDO::getStatus, QUEUED.getStatus())
                .set(DownloadTaskDO::getRetryCount, newRetryCount)) > 0;
    }

}
