/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.microsoft.rest.serializer.JacksonAdapter;
import com.microsoft.rest.serializer.JsonFlatten;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlatteningSerializerTests {
    @Test
    public void canFlatten() throws Exception {
        Foo foo = new Foo();
        foo.bar = "hello.world";
        foo.baz = new ArrayList<>();
        foo.baz.add("hello");
        foo.baz.add("hello.world");
        foo.qux = new HashMap<>();
        foo.qux.put("hello", "world");
        foo.qux.put("a.b", "c.d");
        foo.qux.put("bar.a", "ttyy");
        foo.qux.put("bar.b", "uuzz");

        String serialized = new JacksonAdapter().serialize(foo);
        Assert.assertEquals("{\"$type\":\"foo\",\"properties\":{\"bar\":\"hello.world\",\"props\":{\"baz\":[\"hello\",\"hello.world\"],\"q\":{\"qux\":{\"hello\":\"world\",\"a.b\":\"c.d\",\"bar.b\":\"uuzz\",\"bar.a\":\"ttyy\"}}}}}", serialized);
    }

    @JsonFlatten
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "$type")
    @JsonTypeName("foo")
    @JsonSubTypes({
            @JsonSubTypes.Type(name = "foochild", value = FooChild.class)
    })
    private class Foo {
        @JsonProperty(value = "properties.bar")
        private String bar;
        @JsonProperty(value = "properties.props.baz")
        private List<String> baz;
        @JsonProperty(value = "properties.props.q.qux")
        private Map<String, String> qux;
        @JsonProperty(value = "props.empty")
        private Integer empty;
    }

    @JsonFlatten
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "$type")
    @JsonTypeName("foochild")
    private class FooChild extends Foo {
    }

    @Test
    public void canSerializeMapKeysWithDotAndSlash() throws Exception {
        String serialized = new JacksonAdapter().serialize(prepareSchoolModel());
        Assert.assertEquals("{\"teacher\":{\"students\":{\"af.B/D\":{},\"af.B/C\":{}}},\"tags\":{\"foo.aa\":\"bar\",\"x.y\":\"zz\"},\"properties\":{\"name\":\"school1\"}}", serialized);
    }

    @JsonFlatten
    private class School {
        @JsonProperty(value = "teacher")
        private Teacher teacher;

        @JsonProperty(value = "properties.name")
        private String name;

        @JsonProperty(value = "tags")
        private Map<String, String> tags;

        public School withTeacher(Teacher teacher) {
            this.teacher = teacher;
            return this;
        }

        public School withName(String name) {
            this.name = name;
            return this;
        }

        public School withTags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }
    }

    private class Student {
    }

    private class Teacher {
        @JsonProperty(value = "students")
        private Map<String, Student> students;

        public Teacher withStudents(Map<String, Student> students) {
            this.students = students;
            return this;
        }
    }

    private School prepareSchoolModel() {
        Teacher teacher = new Teacher();

        Map<String, Student> students = new HashMap<String, Student>();
        students.put("af.B/C", new Student());
        students.put("af.B/D", new Student());

        teacher.withStudents(students);

        School school = new School().withName("school1");
        school.withTeacher(teacher);

        Map<String, String> schoolTags = new HashMap<String, String>();
        schoolTags.put("foo.aa", "bar");
        schoolTags.put("x.y", "zz");

        school.withTags(schoolTags);

        return school;
    }
}
