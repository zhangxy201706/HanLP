/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/5/3 11:34</create-date>
 *
 * <copyright file="BinTrie.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.collection.trie.bintrie;

import com.hankcs.hanlp.corpus.io.ByteArray;
import com.hankcs.hanlp.corpus.io.IOUtil;
import com.hankcs.hanlp.utility.CharUtility;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.*;
import static com.hankcs.hanlp.utility.Predefine.logger;

/**
 * 首字直接分配内存，之后二分动态数组的Trie树，能够平衡时间和空间
 * @author hankcs
 */
public class BinTrie<V> extends BaseNode<V>
{
    private int size;
    public BinTrie()
    {
        child = new BaseNode[65535];    // (int)Character.MAX_VALUE
        size = 0;
        status = Status.NOT_WORD_1;
    }

    /**
     * 插入一个词
     *
     * @param key
     * @param value
     */
    public void put(String key, V value)
    {
        BaseNode branch = this;
        char[] chars = key.toCharArray();
        for (int i = 0; i < chars.length - 1; ++i)
        {
            // 除了最后一个字外，都是继续
            branch.addChild(newNode(chars[i], Status.NOT_WORD_1, null));
            branch = branch.getChild(chars[i]);
        }
        // 最后一个字加入时属性为end
        if (branch.addChild(newNode(chars[chars.length - 1], Status.WORD_END_3, value)))
        {
            ++size; // 维护size
        }
    }

    /**
     * 动态决定初始化哪种节点
     * @param c
     * @param status
     * @param value
     * @return
     */
    protected BaseNode<V> newNode(char c, Status status, V value)
    {
        return new Node<V>(c, status, value);
    }

    /**
     * 删除一个词
     * @param key
     */
    public void remove(String key)
    {
        BaseNode branch = this;
        char[] chars = key.toCharArray();
        for (int i = 0; i < chars.length - 1; ++i)
        {
            if (branch == null) return;
            branch = branch.getChild(chars[i]);
        }
        // 最后一个字设为undefined
        if (branch.addChild(newNode(chars[chars.length - 1], Status.UNDEFINED_0, value)))
        {
            --size;
        }
    }

    public boolean containsKey(String key)
    {
        BaseNode branch = this;
        char[] chars = key.toCharArray();
        for (char aChar : chars)
        {
            if (branch == null) return false;
            branch = branch.getChild(aChar);
        }

        return branch != null && (branch.status == Status.WORD_END_3 || branch.status == Status.WORD_MIDDLE_2);
    }

    public V get(String key)
    {
        BaseNode branch = this;
        char[] chars = key.toCharArray();
        for (char aChar : chars)
        {
            if (branch == null) return null;
            branch = branch.getChild(aChar);
        }

        if (branch == null) return null;
        // 下面这句可以保证只有成词的节点被返回
        if (!(branch.status == Status.WORD_END_3 || branch.status == Status.WORD_MIDDLE_2)) return null;
        return (V) branch.getValue();
    }

    /**
     * 获取键值对集合
     * @return
     */
    public Set<Map.Entry<String, V>> entrySet()
    {
        Set<Map.Entry<String, V>> entrySet = new TreeSet<Map.Entry<String, V>>();
        StringBuilder sb = new StringBuilder();
        for (BaseNode node : child)
        {
            if (node == null) continue;
            node.walk(new StringBuilder(sb.toString()), entrySet);
        }
        return entrySet;
    }

    /**
     * 前缀查询
     * @param key 查询串
     * @return 键值对
     */
    public Set<Map.Entry<String, V>> prefixSearch(String key)
    {
        Set<Map.Entry<String, V>> entrySet = new TreeSet<>();
        StringBuilder sb = new StringBuilder(key.substring(0, key.length() - 1));
        BaseNode branch = this;
        char[] chars = key.toCharArray();
        for (char aChar : chars)
        {
            if (branch == null) return entrySet;
            branch = branch.getChild(aChar);
        }

        if (branch == null) return entrySet;
        branch.walk(sb, entrySet);
        return entrySet;
    }

    /**
     * 前缀查询，包含值
     *
     * @param key 键
     * @return 键值对列表
     */
    public LinkedList<Map.Entry<String, V>> commonPrefixSearchWithValue(String key)
    {
        char[] chars = key.toCharArray();
        return commonPrefixSearchWithValue(chars, 0);
    }

    /**
     * 前缀查询，通过字符数组来表示字符串可以优化运行速度
     * @param chars 字符串的字符数组
     * @param begin 开始的下标
     * @return
     */
    public LinkedList<Map.Entry<String, V>> commonPrefixSearchWithValue(char[] chars, int begin)
    {
        LinkedList<Map.Entry<String, V>> result = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        BaseNode branch = this;
        for (int i = begin; i < chars.length; ++i)
        {
            char aChar = chars[i];
            branch = branch.getChild(aChar);
            if (branch == null || branch.status == Status.UNDEFINED_0) return result;
            sb.append(aChar);
            if (branch.status == Status.WORD_MIDDLE_2 || branch.status == Status.WORD_END_3)
            {
                result.add(new AbstractMap.SimpleEntry<>(sb.toString(), (V) branch.value));
            }
        }

        return result;
    }

    @Override
    protected boolean addChild(BaseNode node)
    {
        boolean add = false;
        char c = node.getChar();
        BaseNode target = getChild(c);
        if (target == null)
        {
            child[c] = node;
            add = true;
        }
        else
        {
            switch (node.status)
            {
                case UNDEFINED_0:
                    if (target.status != Status.NOT_WORD_1)
                    {
                        target.status = Status.NOT_WORD_1;
                        add = true;
                    }
                    break;
                case NOT_WORD_1:
                    if (target.status == Status.WORD_END_3)
                    {
                        target.status = Status.WORD_MIDDLE_2;
                    }
                    break;
                case WORD_END_3:
                    if (target.status == Status.NOT_WORD_1)
                    {
                        target.status = Status.WORD_MIDDLE_2;
                    }
                    if (target.getValue() == null)
                    {
                        add = true;
                    }
                    target.setValue(node.getValue());
                    break;
            }
        }
        return add;
    }

    public int size()
    {
        return size;
    }

    @Override
    protected char getChar()
    {
        return 0;   // 根节点没有char
    }

    @Override
    public BaseNode getChild(char c)
    {
        return child[c];
    }

    /**
     * 返回一个空白的子节点，根据自己是否智能返回的节点类型不同
     * @return
     */
    @Override
    protected BaseNode<V> newInstance()
    {
        return new Node<V>();
    }

    public boolean save(String path)
    {
        try
        {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(path));
            for (BaseNode node : child)
            {
                if (node == null)
                {
                    out.writeInt(0);
                }
                else
                {
                    out.writeInt(1);
                    node.walkToSave(out);
                }
            }
            out.close();
        }
        catch (Exception e)
        {
            logger.warning("保存到" + path + "失败" + CharUtility.exceptionToString(e));
            return false;
        }

        return true;
    }

    /**
     * 从磁盘加载二分数组树
     * @param path 路径
     * @param value 额外提供的值数组，按照值的字典序。（之所以要求提供它，是因为泛型的保存不归树管理）
     * @return 是否成功
     */
    public boolean load(String path, V[] value)
    {
        byte[] bytes = IOUtil.readBytes(path);
        if (bytes == null) return false;
        ValueArray valueArray = new ValueArray(value);
        ByteArray byteArray = new ByteArray(bytes);
        for (int i = 0; i < child.length; ++i)
        {
            int flag = byteArray.nextInt();
            if (flag == 1)
            {
                child[i] = newInstance();
                child[i].walkToLoad(byteArray, valueArray);
            }
        }
        size = value.length;

        return true;
    }
}
