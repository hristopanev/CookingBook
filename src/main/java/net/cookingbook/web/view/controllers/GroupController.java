package net.cookingbook.web.view.controllers;

import net.cookingbook.service.*;
import net.cookingbook.service.models.services.GroupServiceModel;
import net.cookingbook.service.models.services.PostServiceModel;
import net.cookingbook.service.models.services.RateServiceModel;
import net.cookingbook.service.models.services.UserServiceModel;
import net.cookingbook.web.base.BaseController;
import net.cookingbook.web.view.models.binding.GroupCreateBindingModel;
import net.cookingbook.web.view.models.binding.PostCreateBindingModel;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/groups")
public class GroupController extends BaseController {

    private final GroupService groupService;
    private final UserService userService;
    private final PostService postService;
    private final RateService rateService;
    private final ModelMapper modelMapper;
    private CloudinaryService cloudinaryService;


    @Autowired
    public GroupController(GroupService groupService, UserService userService, PostService postService, RateService rateService, ModelMapper modelMapper, CloudinaryService cloudinaryService) {
        super(userService);
        this.groupService = groupService;
        this.userService = userService;
        this.postService = postService;
        this.rateService = rateService;
        this.modelMapper = modelMapper;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView createGroup(Principal principal, ModelAndView modelAndView) {
        var user = getUsername(principal);
        modelAndView.addObject("username", user.getUsername());

        return super.view("groups/create", modelAndView);
    }

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView createGroup(@ModelAttribute GroupCreateBindingModel createBindingModel, Principal principal) {

        var user = getUsername(principal);
        createBindingModel.setUsers(user);
        GroupServiceModel groupServiceModel = this.modelMapper.map(createBindingModel, GroupServiceModel.class);

        groupServiceModel.setUsers(
                this.userService.findAllUsers()
                        .stream()
                        .filter(u -> createBindingModel.getUsers().getId().contains(u.getId()))
                        .collect(Collectors.toList())
        );
        this.groupService.createGroup(groupServiceModel);
        return super.redirect("/users/groups");
    }

    @GetMapping("/group/{id}")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView group(Principal principal, @PathVariable String id, ModelAndView modelAndView) {
        var user = userService.findUserByUserName(principal.getName());
        var description = this.groupService.findById(id).getDescription();
        var isJoined = this.groupService.isJoined(id, user);
        var group = this.groupService.findById(id);

        var posts = groupService.findById(id).getPosts()
                .stream()
                .filter(p -> p.getId().contains(group.getPosts().get(0).getId()))
                .map(p -> this.modelMapper.map(p, PostServiceModel.class))
                .collect(Collectors.toList());

        modelAndView.addObject("isJoined", isJoined);
        modelAndView.addObject("groupID", id);
        modelAndView.addObject("username", user.getUsername());
        modelAndView.addObject("description", description);
        modelAndView.addObject("posts", posts);

        return super.view("/groups/group", modelAndView);
    }

    @PostMapping("/join/{id}")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView joinGroupConfig(Principal principal, @PathVariable String id) {
        GroupServiceModel groupServiceModel = this.groupService.findById(id);
        UserServiceModel userServiceModel = this.userService.findUserByUserName(principal.getName());

        groupServiceModel.getUsers().add(userServiceModel);

        if (!this.groupService.joinGroup(groupServiceModel)) {
            throw new IllegalArgumentException("Something went wrong!");
        }

        return super.redirect("/groups/group/" + id);
    }

    @PostMapping("/leave/{id}")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView leaveGroup(Principal principal, @PathVariable String id) {
        UserServiceModel user = this.userService.findUserByUserName(principal.getName());
        groupService.leaveGroup(user.getId(), id);

        return super.redirect("/groups/group/" + id);
    }

    @GetMapping("/edit/{id}")
    @PreAuthorize(("isAuthenticated()"))
    public ModelAndView editGroup(Principal principal, @PathVariable String id, ModelAndView modelAndView) {
        var group = this.groupService.findById(id);

        modelAndView.addObject("group", group);
        modelAndView.addObject("groupID", group.getId());
        modelAndView.addObject("username", principal.getName());

        return super.view("/groups/edit-group", modelAndView);
    }

    @PostMapping("/edit/{id}")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView editGroupConfig(@PathVariable String id, @ModelAttribute GroupCreateBindingModel model) {
        GroupServiceModel groupServiceModel = this.modelMapper.map(model, GroupServiceModel.class);

        this.groupService.editGroup(id, groupServiceModel);

        return super.redirect("/groups/group/" + id);
    }


    @GetMapping("/create-post/{id}")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView addPost(@PathVariable String id, Principal principal, ModelAndView modelAndView) {
        var user = getUsername(principal);
        modelAndView.addObject("username", user.getUsername());
        modelAndView.addObject("groupID", this.groupService.findById(id).getId());
        return super.view("groups/create-post", modelAndView);
    }

    @PostMapping("/create-post/{id}")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView addPostConfirm(@PathVariable String id, @ModelAttribute PostCreateBindingModel model, Principal principal) throws IOException {
        UserServiceModel user = this.userService.findUserByUserName(principal.getName());
        GroupServiceModel groupServiceModel = this.groupService.findById(id);
        PostServiceModel postServiceModel = this.modelMapper.map(model, PostServiceModel.class);
        RateServiceModel rateServiceModel = new RateServiceModel();

        postServiceModel.setUploader(user);
        postServiceModel.setPostTime(new Date());
        postServiceModel.setImageUrl(this.cloudinaryService.uploadImage(model.getImage()));

        this.postService.createPost(postServiceModel);
        this.rateService.create(rateServiceModel, postServiceModel);

        if (!this.groupService.cretePost(groupServiceModel, postServiceModel)) {
            throw new IllegalArgumentException("Something went wrong!");
        }

        return super.redirect("/groups/group/" + id);
    }

    @PostMapping("/delete/{id}")
    public ModelAndView deleteGroup(@PathVariable String id) {
        this.groupService.deleteGroup(id);

        return super.redirect("/home");
    }

    @GetMapping("/all")
    public ModelAndView allGroup(Principal principal, ModelAndView modelAndView) {
        List<GroupServiceModel> groups = this.groupService.findAllGroups();
        var user = this.userService.findUserByUserName(principal.getName());

        modelAndView.addObject("username", user.getUsername());
        modelAndView.addObject("groups", groups);

        return super.view("/groups/all", modelAndView);
    }
}