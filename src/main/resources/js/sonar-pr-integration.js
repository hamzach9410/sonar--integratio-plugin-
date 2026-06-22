(function($) {
    'use strict';

    $(document).ready(function() {
        if ($('.sonar-pr-list-badge').length > 0) {
            decoratePRList();
        }
        
        if ($('#sonar-repo-status-content').length > 0) {
            decorateRepoDashboard();
        }

        if ($('#sonar-pr-sidebar-content').length > 0) {
            decoratePRSidebar();
        }

        if ($('#sonar-pr-toolbar-content').length > 0) {
            decoratePRToolbar();
        }

        if ($('.sonar-branch-list-badge').length > 0) {
            decorateBranchList();
        }
    });

    function decorateBranchList() {
        $('.sonar-branch-list-badge').each(function() {
            var $badge = $(this);
            var $row = $badge.closest('tr');
            var branchName = $row.attr('data-branch-name') || $row.find('.branch-name').text();
            var repoId = $('meta[name="repositoryId"]').attr('content');

            if (!branchName || !repoId) return;

            $.ajax({
                type: 'GET',
                url: AJS.contextPath() + '/plugins/servlet/sonar/proxy/' + repoId + '/status/branch/' + encodeURIComponent(branchName),
                success: function(data) {
                    var statusClass = data.status === 'OK' ? 'aui-lozenge-success' : 'aui-lozenge-error';
                    $badge.html('<span class="aui-lozenge ' + (data.status === 'OK' ? 'aui-lozenge-subtle aui-lozenge-success' : 'aui-lozenge-error') + '">' + data.status + '</span>');
                }
            });
        });
    }

    // 2. Observe Bitbucket's UI for annotations being rendered
    $(document).on('click', '.insight-annotation-content', function() {
        var $annotation = $(this);
        var issueKey = $annotation.attr('data-annotation-key');
        
        if ($annotation.closest('.insight-report-content').find('.insight-report-title').text().indexOf('SonarQube') === -1) {
            return;
        }

        if ($annotation.find('.sonar-quick-actions').length === 0) {
            addSonarActions($annotation, issueKey);
        }
    });

    function getPrIdFromUrl() {
        var match = window.location.pathname.match(/\/pull-requests\/(\d+)/);
        return match ? match[1] : null;
    }

    function getRepoId() {
        return $('#pull-request-header').attr('data-repository-id') ||
               $('meta[name="repositoryId"]').attr('content') || null;
    }

    function decoratePRList() {
        $('.sonar-pr-list-badge').each(function() {
            var $badge = $(this);
            var $row = $badge.closest('tr');
            var prId = $row.attr('data-pull-request-id') || $row.find('.pull-request-title').attr('href').split('/').pop();
            var repoId = $('#pull-request-header').attr('data-repository-id') || $('meta[name="repositoryId"]').attr('content');

            if (!prId || !repoId) return;

            $.ajax({
                type: 'GET',
                url: AJS.contextPath() + '/plugins/servlet/sonar/proxy/' + repoId + '/status/pr/' + prId,
                success: function(data) {
                    var statusClass = data.status === 'OK' ? 'aui-lozenge-success' : 'aui-lozenge-error';
                    $badge.html('<span class="aui-lozenge ' + statusClass + '">' + data.status + '</span>');
                }
            });
        });
    }

    function decorateRepoDashboard() {
        var repoId = $('meta[name="repositoryId"]').attr('content');
        if (!repoId) return;

        $.ajax({
            type: 'GET',
            url: AJS.contextPath() + '/plugins/servlet/sonar/proxy/' + repoId + '/status/repo',
            success: function(data) {
                var statusClass = data.status === 'OK' ? 'aui-lozenge-success' : 'aui-lozenge-error';
                var html = '<div><span class="aui-lozenge ' + statusClass + ' aui-lozenge-large">' + data.status + '</span></div>';
                html += '<div style="margin-top: 10px;">';
                html += '<span>Bugs: ' + (data.bugs || 0) + '</span> | ';
                html += '<span>Vulnerabilities: ' + (data.vulnerabilities || 0) + '</span> | ';
                html += '<span>Code Smells: ' + (data.code_smells || 0) + '</span>';
                html += '</div>';
                
                $('#sonar-repo-status-content').html(html);
                if (data.link) {
                    $('#sonar-repo-link').attr('href', data.link);
                }
            },
            error: function() {
                $('#sonar-repo-status-content').html('<span class="aui-message aui-message-error">Failed to load SonarQube status.</span>');
            }
        });
    }

    function decoratePRSidebar() {
        var repositoryId = getRepoId();
        var prId = getPrIdFromUrl();
        if (!repositoryId || !prId) return;

        $.ajax({
            type: 'GET',
            url: AJS.contextPath() + '/plugins/servlet/sonar/proxy/' + repositoryId + '/status/pr/' + prId,
            success: function(data) {
                if (!data || !data.status) return;

                var statusClass = data.status === 'OK' ? 'aui-lozenge-success' : 'aui-lozenge-error';
                var html = '<div style="margin-bottom: 8px;"><span class="aui-lozenge ' + statusClass + ' aui-lozenge-large">' + data.status + '</span></div>';
                
                html += '<div class="sonar-metrics-list" style="font-size: 13px; line-height: 1.6;">';
                html += '<div><strong>Bugs:</strong> ' + (data.bugs || 0) + '</div>';
                html += '<div><strong>Vulnerabilities:</strong> ' + (data.vulnerabilities || 0) + '</div>';
                html += '<div><strong>Code Smells:</strong> ' + (data.code_smells || 0) + '</div>';
                html += '<div><strong>Coverage:</strong> ' + (data.coverage || '0%') + '</div>';
                html += '<div><strong>Duplication:</strong> ' + (data.duplicated_lines_density || '0%') + '</div>';
                html += '</div>';

                if (data.conditions && data.conditions.length > 0) {
                    var failures = data.conditions.filter(function(c) { return c.status !== 'OK'; });
                    if (failures.length > 0) {
                        html += '<div style="margin-top: 10px; padding-top: 5px; border-top: 1px dotted #ccc; font-size: 11px; color: #d04437;">';
                        failures.forEach(function(c) {
                            html += '<div>&times; ' + c.metricKey.replace(/_/g, ' ') + '</div>';
                        });
                        html += '</div>';
                    }
                }

                if (data.link) {
                    html += '<div style="margin-top: 10px;"><a href="' + data.link + '" target="_blank" class="aui-button aui-button-link" style="padding:0; height:auto; line-height:1;">Go to SonarQube &raquo;</a></div>';
                }
                
                $('#sonar-pr-sidebar-content').html(html);
            },
            error: function() {
                $('#sonar-pr-sidebar-content').html('<span class="aui-message aui-message-error">Failed to load status.</span>');
            }
        });
    }

    function decoratePRToolbar() {
        var repositoryId = getRepoId();
        var prId = getPrIdFromUrl();
        if (!repositoryId || !prId) return;

        $.ajax({
            type: 'GET',
            url: AJS.contextPath() + '/plugins/servlet/sonar/proxy/' + repositoryId + '/status/pr/' + prId,
            success: function(data) {
                if (!data || !data.status) return;
                var statusClass = data.status === 'OK' ? 'aui-lozenge-subtle aui-lozenge-success' : 'aui-lozenge-error';
                var html = '<span class="aui-lozenge ' + statusClass + '" title="SonarQube Quality Gate">Sonar: ' + data.status + '</span>';
                $('#sonar-pr-toolbar-content').html(html).show();
            }
        });
    }


    function addSonarActions($container, issueKey) {
        var repositoryId = getRepoId();
        if (!repositoryId) return;

        var $actions = $('<div class="sonar-quick-actions" style="margin-top: 10px; border-top: 1px solid #ddd; padding-top: 5px;"></div>');
        
        $actions.append('<button class="aui-button aui-button-subtle sonar-action" style="margin-right:5px;" data-action="transition" data-param="false-positive">Set False Positive</button>');
        $actions.append('<button class="aui-button aui-button-subtle sonar-action" style="margin-right:5px;" data-action="transition" data-param="wontfix">Won\'t Fix</button>');
        $actions.append('<button class="aui-button aui-button-subtle sonar-action" style="margin-right:5px;" data-action="assign">Assign to Me</button>');
        $actions.append('<button class="aui-button aui-button-subtle sonar-action" data-action="comment">Add Comment</button>');
        
        $container.append($actions);

        $actions.find('.sonar-action').on('click', function(e) {
            e.stopPropagation();
            var $btn = $(this);
            var action = $btn.attr('data-action');
            var param = $btn.attr('data-param');
            var data = { issue: issueKey };

            if (action === 'transition') {
                data.transition = param;
            } else if (action === 'assign') {
                // Get current user from Bitbucket JS API
                var user = (window.bitbucket && window.bitbucket.getCurrentUser && window.bitbucket.getCurrentUser().getName()) 
                           || (typeof JIRA !== 'undefined' && JIRA.Users && JIRA.Users.LoggedInUser && JIRA.Users.LoggedInUser.userName);
                
                if (!user) {
                    user = prompt('Please enter your SonarQube username:');
                }
                if (!user) return;
                data.assignee = user;
            } else if (action === 'comment') {
                var comment = prompt('Enter your comment:');
                if (!comment) return;
                data.text = comment;
            }
            
            $btn.prop('disabled', true).text('Working...');

            $.ajax({
                type: 'POST',
                url: AJS.contextPath() + '/plugins/servlet/sonar/proxy/' + repositoryId + '/' + action,
                data: data,
                success: function() {
                    $btn.text('Done!');
                    if (action === 'transition') {
                        $container.fadeOut(300, function() { $(this).remove(); });
                    } else {
                        setTimeout(function() { $btn.prop('disabled', false).text(action === 'assign' ? 'Assign to Me' : 'Add Comment'); }, 2000);
                    }
                },
                error: function(xhr) {
                    $btn.prop('disabled', false).text('Error');
                    console.error('SonarQube Proxy Error:', xhr.responseText);
                    alert('Error: ' + xhr.responseText);
                }
            });
        });
    }
})(jQuery);
