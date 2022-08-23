package com.example.simpleRewardDapp.controller;

import com.example.simpleRewardDapp.dto.*;
import com.example.simpleRewardDapp.entity.*;
import com.example.simpleRewardDapp.repository.CartItemRepository;
import com.example.simpleRewardDapp.repository.CartRepository;
import com.example.simpleRewardDapp.repository.ItemRepository;
import com.example.simpleRewardDapp.repository.MemberRepository;
import com.example.simpleRewardDapp.service.CartItemService;
import com.example.simpleRewardDapp.service.OrderItemService;
import com.example.simpleRewardDapp.service.OrderService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class CartController {

    private final MemberRepository memberRepository;
    private final CartRepository cartRepository;
    private final ItemRepository itemRepository;
    private final CartItemRepository cartItemRepository;

    private final OrderItemService orderItemService;
    private final CartItemService cartItemService;
    private final OrderService orderService;

    @PostMapping("/cart/add")
    public Result addCart(@RequestBody CartParam cartParam) {
        Member member = getMember(cartParam);
        Cart cart = getCart(member);
        CartDto cartDto = new CartDto(cart.getId(), member.getUsername());
        for (CartItemDto cartItemDto : cartParam.getCartItemDtos()) {
            Item item = getItem(cartItemDto.getItemDto());

            CartItem cartItem = cartItemService.createCartItem(item, cart, cartItemDto.getQuantity(), item.getPrice());

            ItemDto itemDto = new ItemDto(item.getId(), item.getName(), item.getQuantity());
            CartItemDto cartItemDtoResult =
                    new CartItemDto(cartItem.getId(), cartItem.getQuantity(), cartItem.getPrice(), itemDto);

            cartDto.getCartItemDtos().add(cartItemDtoResult);
        }
        return new Result<CartDto>(cartDto);
    }

    /**
     * 중복 상품이 있을 경우 하나씩 삭제. 임시방편으로 처리해놓음.
     */
    @PostMapping("/cart/sub")
    public Result subCart(@RequestBody CartParam cartParam) {
        Member member = getMember(cartParam);
        Cart cart = getCart(member);
        for (CartItemDto cartItemDto : cartParam.getCartItemDtos()) {
            List<CartItem> cartItems = cartItemRepository.findByItemName(cartItemDto.getItemDto().getName());
            cartItemService.deleteCartItem(cartItems.get(0));
        }
        List<CartItem> cartItems = cartItemRepository.findCartItemsByCartId(cart.getId());
        List<CartItemDto> cartItemDtos =
                cartItems.stream().map(x -> new CartItemDto(x.getId(), x.getQuantity(), x.getPrice(),
                        new ItemDto(x.getItem().getId(), x.getItem().getName(), x.getItem().getQuantity())))
                        .collect(Collectors.toList());
        CartDto cartDto = new CartDto(cart.getId(), member.getUsername(), cartItemDtos);
        return new Result<CartDto>(cartDto);
    }

    /**
     * 딱 카트에 있는 모든 상품 주문 가능하게만 해놓음. 보유 캐시나 포인트 사용 x
     */
    @PostMapping("/cart/orderAll")
    public Result orderAllInCart(@RequestBody MemberDto memberDto) {
        Optional<Member> findMember = memberRepository.findByUsername(memberDto.getUsername());
        Member member = findMember.orElseThrow(() -> new RuntimeException("찾는 멤버가 없습니다."));
        Cart cart = getCart(member);
        List<CartItem> cartItems = cartItemRepository.findCartItemsByCartId(cart.getId());
        Order order = orderService.createOrder(member);
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = orderItemService
                    .createOrderItem(cartItem.getItem(), cart, cartItem.getQuantity(), cartItem.getPrice(), order);
            //카트에서 제거되면 아이템 재고 복구
            cartItemService.deleteCartItem(cartItem);
        }

        OrderDto orderDto = getOrderDto(member, order);

        return new Result<OrderDto>(orderDto);

    }

    private Item getItem(ItemDto itemDto) {
        Optional<Item> findItem = itemRepository.findByName(itemDto.getName());
        Item item = findItem.orElseThrow(() -> new RuntimeException("해당 아이템이 없습니다."));
        return item;
    }

    private Member getMember(CartParam cartParam) {
        Optional<Member> findMember = memberRepository.findByUsername(cartParam.getUsername());
        return findMember.orElseThrow(() -> new RuntimeException("해당 사용자가 없습니다."));
    }

    private Cart getCart(Member member) {
        Optional<Cart> findCart = cartRepository.findByMemberId(member.getId());
        return findCart.orElseThrow(() -> new RuntimeException("해당 장바구니가 없습니다."));
    }
    private OrderDto getOrderDto(Member member, Order order) {
        return new OrderDto(order.getId(), member.getUsername(), order.getSavedPoint(), order.getTotalPrice(),
                order.getOrderItems().stream()
                        .map(x -> new OrderItemDto(x.getId(), x.getQuantity(),
                                new ItemDto(x.getItem().getId(), x.getItem().getName(), x.getItem().getQuantity())
                        ))
                        .collect(Collectors.toList()));
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
    }

    /**
     * 2개 클래스 하나로 통합하는 클래스 임시 생성
     */
    @Data
    @AllArgsConstructor
    static class CartParam {
        private String username;
        private List<CartItemDto> cartItemDtos;
    }
}
