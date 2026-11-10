package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xuecheng.base.model.exception.XueChengPlusException;
import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.mapper.TeachplanMediaMapper;
import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachplanService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author Mr.M
 * @version 1.0
 * @description 课程计划service接口实现类
 * @date 2022/9/9 11:14
 */
@Service
public class TeachplanServiceImpl implements TeachplanService {

    @Autowired
    TeachplanMapper teachplanMapper;

    @Autowired
    TeachplanMediaMapper teachplanMediaMapper;


    @Override
    public List<TeachplanDto> findTeachplanTree(long courseId) {
        return teachplanMapper.selectTreeNodes(courseId);
    }

    @Override
    public void saveTeachplan(SaveTeachplanDto teachplanDto) {

        //课程计划id
        Long id = teachplanDto.getId();
        //修改课程计划
        if (id != null) {
            Teachplan teachplan = teachplanMapper.selectById(id);
            BeanUtils.copyProperties(teachplanDto, teachplan);
            teachplanMapper.updateById(teachplan);
        }
        else {
            //取出同父同级别的课程计划数量
            int count = getTeachplanCount(teachplanDto.getCourseId(), teachplanDto.getParentid());
            Teachplan teachplanNew = new Teachplan();
            //设置排序号
            teachplanNew.setOrderby(count + 1);
            BeanUtils.copyProperties(teachplanDto, teachplanNew);

            teachplanMapper.insert(teachplanNew);

        }

    }

    @Override
    public void deleteTeachplan(Long courseId) {
        Teachplan teachplan = teachplanMapper.selectById(courseId);
        if (teachplan.getParentid() == 0) {
            if (teachplanMapper.selectSonNodes(courseId).isEmpty()) {
                teachplanMapper.deleteById(courseId);
            }
            else {
                throw new XueChengPlusException("删除失败，该课程有子课程");
            }
        }
        else {
            teachplanMapper.deleteById(courseId);
            LambdaQueryWrapper<TeachplanMedia> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TeachplanMedia::getCourseId, courseId);
            teachplanMediaMapper.delete(wrapper);
        }
    }

    @Override
    public void moveTeachplan(String moveType, Long teachplanId) {
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        Integer grade = teachplan.getGrade();
        if (grade == 1) {
            LambdaQueryWrapper<Teachplan> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Teachplan::getGrade, grade)
                    .eq(Teachplan::getParentid, teachplan.getParentid())
                    .orderByAsc(Teachplan::getOrderby);
            List<Teachplan> teachplanList = teachplanMapper.selectList(wrapper);
            exchangeTeachplan(teachplanList, teachplan, moveType);
        }
        else {
            LambdaQueryWrapper<Teachplan> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Teachplan::getCourseId, teachplan.getCourseId())
                    .eq(Teachplan::getParentid, teachplan.getParentid())
                    .orderByAsc(Teachplan::getOrderby);
            List<Teachplan> teachplanList = teachplanMapper.selectList(wrapper);
            exchangeTeachplan(teachplanList, teachplan, moveType);
        }
    }

    @Transactional
    @Override
    public TeachplanMedia associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {
        //教学计划id
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        if (teachplan == null) {
            XueChengPlusException.cast("教学计划不存在");
        }
        Integer grade = teachplan.getGrade();
        if (grade != 2) {
            XueChengPlusException.cast("只允许第二级教学计划绑定媒资文件");
        }
        //课程id
        Long courseId = teachplan.getCourseId();

        //先删除原来该教学计划绑定的媒资
        teachplanMediaMapper.delete(new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId, teachplanId));

        //再添加教学计划与媒资的绑定关系
        TeachplanMedia teachplanMedia = new TeachplanMedia();
        teachplanMedia.setCourseId(courseId);
        teachplanMedia.setTeachplanId(teachplanId);
        teachplanMedia.setMediaFilename(bindTeachplanMediaDto.getFileName());
        teachplanMedia.setMediaId(bindTeachplanMediaDto.getMediaId());
        teachplanMedia.setCreateDate(LocalDateTime.now());
        teachplanMediaMapper.insert(teachplanMedia);
        return teachplanMedia;
    }

    private void exchangeTeachplan(List<Teachplan> teachplanList, Teachplan teachplan, String moveType) {
        int index = teachplanList.indexOf(teachplan);
        if (Objects.equals(moveType, "movedown")) {
            if (index == teachplanList.size() - 1) {
                throw new XueChengPlusException("已经处在最下的位置,无法继续下移");
            }
            Teachplan teachplan1 = teachplanList.get(index + 1);
            teachplan.setOrderby(teachplan.getOrderby() + 1);
            teachplan1.setOrderby(teachplan1.getOrderby() - 1);
            teachplanMapper.updateById(teachplan);
            teachplanMapper.updateById(teachplan1);
        }
        else {
            if (index == 0) {
                throw new XueChengPlusException("已经处在最上的位置,无法继续上移");
            }
            Teachplan teachplan1 = teachplanList.get(index - 1);
            teachplan.setOrderby(teachplan.getOrderby() - 1);
            teachplan1.setOrderby(teachplan1.getOrderby() + 1);
            teachplanMapper.updateById(teachplan);
            teachplanMapper.updateById(teachplan1);
        }
    }

    /**
     * @param courseId 课程id
     * @param parentId 父课程计划id
     * @return int 最新排序号
     * @description 获取最新的排序号
     * @author Mr.M
     * @date 2022/9/9 13:43
     */
    private int getTeachplanCount(long courseId, long parentId) {
        LambdaQueryWrapper<Teachplan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Teachplan::getCourseId, courseId);
        queryWrapper.eq(Teachplan::getParentid, parentId);
        return teachplanMapper.selectCount(queryWrapper);
    }
}