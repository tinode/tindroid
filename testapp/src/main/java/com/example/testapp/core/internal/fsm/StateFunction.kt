package com.example.testapp.core.internal.fsm

internal typealias StateFunction<S, E> = FiniteStateMachine<S, E>.(S, E) -> S
