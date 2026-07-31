package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.common.R;
import com.japy.entity.Novel;
import com.japy.entity.Post;
import com.japy.mapper.NovelMapper;
import com.japy.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
public class NovelController {

    private final NovelMapper novelMapper;
    private final PostMapper postMapper;

    @GetMapping
    public R<List<Map<String, Object>>> list() {
        List<Novel> novels = novelMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Novel n : novels) {
            Long count = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>().eq(Post::getNovelId, n.getId()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", n.getId());
            item.put("title", n.getTitle());
            item.put("author", n.getAuthor());
            item.put("postCount", count);
            result.add(item);
        }
        return R.ok(result);
    }
}
